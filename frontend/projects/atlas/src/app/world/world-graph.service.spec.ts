import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService, provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { DeviceStore } from '../settings/device.store';
import { WorldGraphService } from './world-graph.service';
import { WorldData } from './world.model';

function ok<T>(data: T) {
  return { success: true, message: 'ok', timestamp: '', data };
}
function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, size: 50, number: 0, first: true, last: true, empty: content.length === 0 };
}

const PLANT = { id: 1, nickname: 'Office Fig', species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 0, healthStatus: 'ISSUES_DETECTED', activeTreatmentId: 301 };
const PLAIN_PLANT = { id: 2, nickname: 'Pothos', species: null, commonName: null, nextWaterDays: 4, healthStatus: 'HEALTHY' };
const SPECIES = { id: 10, scientificName: 'Ficus lyrata', commonName: 'Fiddle-leaf Fig' };
const SCAN = { id: 5, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: 'HEALTHY', status: 'COMPLETED', createdAt: '2026-08-18T10:00:00Z', plantId: 1 };
const REMINDER = { id: 601, plantId: 1, plantNickname: 'Office Fig', careType: 'WATERING', frequencyDays: 7, nextDueAt: '2026-08-16T08:00:00Z', enabled: true, recurring: true };
const TREATMENT = { id: 301, plantId: 1, diseaseName: 'Root rot', status: 'IN_PROGRESS', descriptionStatus: 'PENDING', treatmentPlanId: 201, createdAt: '2026-08-12T09:00:00Z' };
const PLAN = { id: 201, plantId: 1, title: 'Root rot', status: 'ACTIVE', createdAt: '2026-08-12T09:00:00Z', steps: [{ ...REMINDER, id: 702, recurring: false, frequencyDays: 0, treatmentPlanId: 201, stepOrder: 2, instruction: 'Check the crown' }] };

describe('WorldGraphService (S3 — the round-2/3 loader)', () => {
  let service: WorldGraphService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideMockModeOff(),
        provideHttpClientTesting(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
        { provide: AuthService, useValue: { getCurrentUser: () => ({ firstName: 'Mo', lastName: 'El', email: 'mo@x.dev', id: 1, status: 'ACTIVE' }) } },
      ],
    });
    service = TestBed.inject(WorldGraphService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.inject(DeviceStore).clear();
    localStorage.clear();
  });

  /** The six calls every load makes, in the order the loader issues them. */
  function flushStageOne(over: { reminders?: unknown } = {}): void {
    http.expectOne('/api/v1/plants?size=50').flush(ok(page([PLANT, PLAIN_PLANT])));
    http.expectOne('/api/v1/species/mine?size=50').flush(ok(page([SPECIES])));
    http.expectOne('/api/v1/identifications?size=50').flush(ok(page([SCAN])));
    const reminders = http.expectOne('/api/v1/reminders');
    if (over.reminders === 'fail') reminders.flush({ success: false, message: 'Service unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    else reminders.flush(ok([REMINDER]));
    http.expectOne('/api/v1/dashboard').flush(ok({ healthSummary: { healthy: 1, issues: 1, unknown: 0 }, overdueReminders: [], todayReminders: [], healthTrends: [], recentScans: [], plantCount: 2, speciesCount: 1 }));
    http.expectOne('/api/v1/users/me/preferences').flush(ok({ visionModelPreference: 'GITHUB_GPT4O', reasoningModelPreference: 'DEEPSEEK_R1' }));
  }

  it('fetches every family and assembles the world', () => {
    let result: WorldData | undefined;
    service.load().subscribe(w => (result = w));

    flushStageOne();
    http.expectOne('/api/v1/plants/1/active-treatments').flush(ok([TREATMENT]));
    http.expectOne('/api/v1/care/plant/1?size=5').flush(ok(page([{ id: 901, plantId: 1, careType: 'WATERING', performedAt: '2026-08-15T08:00:00Z' }])));
    http.expectOne('/api/v1/care/plant/2?size=5').flush(ok(page([])));
    http.expectOne('/api/v1/treatment-plans/201').flush(ok(PLAN));

    expect(result).toBeDefined();
    expect(result!.initialFocus).toBe('n-garden');
    expect(result!.nodes.map(n => n.id)).toEqual(expect.arrayContaining(['n-garden', 'n-plant-1', 'n-species-10', 'n-ident', 'n-account']));
    expect(result!.meta!.plantsIndex).toEqual([
      { id: 1, nickname: 'Office Fig', lastScanId: 5 },
      { id: 2, nickname: 'Pothos', lastScanId: undefined },
    ]);
    expect(result!.meta!.reminders.map(r => r.id)).toEqual([601]);
    expect(result!.meta!.treatmentsIndex[301]).toEqual(expect.objectContaining({ plantId: 1, status: 'IN_PROGRESS', planId: 201, nextStepId: 702, nextStepOrder: 2 }));
    // a PENDING disease description keeps the shell polling, exactly like a scan
    expect(result!.meta!.hasPendingDescription).toBe(true);
    expect(result!.meta!.failures).toEqual([]);
  });

  it('tolerates a failed family — only its own hub degrades', () => {
    let result: WorldData | undefined;
    service.load().subscribe(w => (result = w));

    flushStageOne({ reminders: 'fail' });
    http.expectOne('/api/v1/plants/1/active-treatments').flush(ok([TREATMENT]));
    http.expectOne('/api/v1/care/plant/1?size=5').flush(ok(page([])));
    http.expectOne('/api/v1/care/plant/2?size=5').flush(ok(page([])));
    http.expectOne('/api/v1/treatment-plans/201').flush(ok(PLAN));

    expect(result).toBeDefined();
    expect(result!.meta!.failures.map(f => [f.family, f.status])).toEqual([['reminders', 503]]);
    const reminders = result!.nodes.find(n => n.id === 'n-reminders')!;
    expect(reminders.state).toBe('failed');
    expect(reminders.body).toContain('state--error');
    expect(reminders.body).toContain('Fetch this region');
    // the rest of the board is live, and the geography is untouched
    expect(result!.nodes.find(n => n.id === 'n-plant-1')!.state).not.toBe('failed');
    expect(result!.nodes.find(n => n.id === 'n-garden')!.recap).toBe('2 plants · 1 need water');
  });

  it('issues no request for a family with nothing to fetch', () => {
    service.load().subscribe();

    http.expectOne('/api/v1/plants?size=50').flush(ok(page([PLAIN_PLANT])));
    http.expectOne('/api/v1/species/mine?size=50').flush(ok(page([])));
    http.expectOne('/api/v1/identifications?size=50').flush(ok(page([])));
    http.expectOne('/api/v1/reminders').flush(ok([]));
    http.expectOne('/api/v1/dashboard').flush(ok({ healthSummary: { healthy: 1, issues: 0, unknown: 0 }, overdueReminders: [], todayReminders: [], healthTrends: [], recentScans: [], plantCount: 1, speciesCount: 0 }));
    http.expectOne('/api/v1/users/me/preferences').flush(ok({}));
    http.expectOne('/api/v1/care/plant/2?size=5').flush(ok(page([])));

    // no plant carries an activeTreatmentId, so no treatment or plan is fetched
    expect(http.match(r => r.url.includes('active-treatments'))).toHaveLength(0);
    expect(http.match(r => r.url.includes('treatment-plans'))).toHaveLength(0);
  });
});
