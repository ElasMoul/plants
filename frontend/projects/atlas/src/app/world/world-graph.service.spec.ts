import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService, provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { WorldGraphService } from './world-graph.service';
import { WorldData } from './world.model';

function ok<T>(data: T) {
  return { success: true, message: 'ok', timestamp: '', data };
}
function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, size: 50, number: 0, first: true, last: true, empty: content.length === 0 };
}

describe('WorldGraphService (H5 — round-1 spine fetch + assemble)', () => {
  let service: WorldGraphService;
  let http: HttpTestingController;

  beforeEach(() => {
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

  afterEach(() => http.verify());

  it('fetches plants + species + identifications (no dashboard) and assembles', () => {
    let result: WorldData | undefined;
    service.load().subscribe(w => (result = w));

    http.expectOne('/api/v1/plants?size=50').flush(
      ok(page([{ id: 1, nickname: 'Office Fig', species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 0, healthStatus: 'ISSUES_DETECTED' }])),
    );
    http.expectOne('/api/v1/species/mine?size=50').flush(
      ok(page([{ id: 10, scientificName: 'Ficus lyrata', commonName: 'Fiddle-leaf Fig' }])),
    );
    http.expectOne('/api/v1/identifications?size=50').flush(
      ok(page([{ id: 5, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: 'HEALTHY', status: 'PENDING', createdAt: '2026-08-18T10:00:00Z' }])),
    );

    expect(result).toBeDefined();
    expect(result!.initialFocus).toBe('n-garden');
    const ids = result!.nodes.map(n => n.id);
    expect(ids).toEqual(expect.arrayContaining(['n-garden', 'n-plant-1', 'n-species-10', 'n-ident', 'n-problems', 'n-account']));
    // live user on the account node; pending scan drives polling
    expect(result!.nodes.find(n => n.id === 'n-account')!.recap).toBe('mo@x.dev');
    expect(result!.hasPendingScan).toBe(true);
  });
});
