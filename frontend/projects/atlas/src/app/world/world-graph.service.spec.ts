import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { WorldGraphService } from './world-graph.service';
import { WorldData } from './world.model';

function ok<T>(data: T) {
  return { success: true, message: 'ok', timestamp: '', data };
}
function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, size: 50, number: 0, first: true, last: true, empty: content.length === 0 };
}

describe('WorldGraphService (D1 — fetch + assemble)', () => {
  let service: WorldGraphService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
      ],
    });
    service = TestBed.inject(WorldGraphService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('calls the three endpoints and assembles the garden-focused world', () => {
    let result: WorldData | undefined;
    service.load().subscribe(w => (result = w));

    http.expectOne('/api/v1/dashboard').flush(
      ok({
        healthSummary: { totalPlants: 2, healthyCount: 1, issuesCount: 1, unknownCount: 0 },
        overdueReminders: [],
        todayReminders: [],
        speciesCount: 1,
      }),
    );
    http.expectOne('/api/v1/plants?size=50').flush(
      ok(page([
        { id: 1, nickname: 'Office Fig', species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 0, healthStatus: 'ISSUES_DETECTED' },
        { id: 2, nickname: 'Studio Fig', species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 5, healthStatus: 'HEALTHY' },
      ])),
    );
    http.expectOne('/api/v1/species/mine?size=50').flush(
      ok(page([{ id: 10, scientificName: 'Ficus lyrata', commonName: 'Fiddle-leaf Fig' }])),
    );

    expect(result).toBeDefined();
    expect(result!.initialFocus).toBe('n-garden');
    const ids = result!.nodes.map(n => n.id);
    expect(ids).toEqual(expect.arrayContaining(['n-garden', 'n-plant-1', 'n-plant-2', 'n-species-10', 'n-problems']));
    // the issues-detected plant reads as needing attention
    expect(result!.nodes.find(n => n.id === 'n-plant-1')!.recap).toBe('Needs attention');
  });
});
