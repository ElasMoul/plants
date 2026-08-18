import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { WorldActionsService } from './world-actions.service';
import { WorldStore } from './world.store';

describe('WorldActionsService (H6 — every button works as intended)', () => {
  let actions: WorldActionsService;
  let store: WorldStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...provideSharedCore({ apiBaseUrl: '/api/v1' })],
    });
    actions = TestBed.inject(WorldActionsService);
    store = TestBed.inject(WorldStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('"Add a plant" opens the add-plant form (no request until submit)', () => {
    actions.dispatch('n-garden', 'Add a plant');
    expect(actions.activeForm()).toEqual({ kind: 'add-plant' });
  });

  it('createPlant POSTs, announces, closes the form and requests a reload', () => {
    actions.activeForm.set({ kind: 'add-plant' });
    actions.createPlant({ nickname: 'New Fig', species: 'Ficus lyrata' });
    const req = http.expectOne('/api/v1/plants');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.nickname).toBe('New Fig');
    req.flush({ success: true, message: '', timestamp: '', data: { id: 9, nickname: 'New Fig' } });
    expect(actions.activeForm()).toBeNull();
    expect(store.announcement()).toContain('planted');
    expect(actions.reloadRequested()).toBe(1);
  });

  it('a failed create keeps the form open and names the fact', () => {
    actions.activeForm.set({ kind: 'add-plant' });
    actions.createPlant({ nickname: 'X' });
    http.expectOne('/api/v1/plants').flush({ message: 'Nickname taken' }, { status: 400, statusText: 'Bad' });
    expect(actions.activeForm()).toEqual({ kind: 'add-plant' });
    expect(store.announcement()).toBe('Nickname taken');
  });

  it('"Add note" on a plant opens the note form; addNote PUTs the note', () => {
    actions.dispatch('n-plant-7', 'Add note');
    expect(actions.activeForm()).toEqual({ kind: 'add-note', plantId: 7, plantName: 'this plant' });
    actions.addNote(7, 'leaf unfurled');
    const req = http.expectOne('/api/v1/plants/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.notes).toBe('leaf unfurled');
    req.flush({ success: true, message: '', timestamp: '', data: {} });
    expect(store.announcement()).toContain('camera did not move');
  });

  it('"Try the scan again" POSTs the retry for the latest failed scan', () => {
    (store as unknown as { data: { update: (fn: (d: object) => object) => void } }).data
      .update(d => ({ ...d, latestFailedScanId: 42 }));
    actions.dispatch('n-ident', 'Try the scan again');
    const req = http.expectOne('/api/v1/identifications/42/retry');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: '', timestamp: '', data: {} });
    expect(actions.reloadRequested()).toBe(1);
  });

  it('"Check health again" runs a real timed call and reports UP', () => {
    actions.dispatch('n-platform', 'Check health again');
    const req = http.expectOne(r => r.url === '/api/v1/plants');
    req.flush({ success: true, message: '', timestamp: '', data: {} });
    expect(store.announcement()).toMatch(/UP/);
  });

  it('offline queues everything and fires no request', () => {
    store.probeOffline.set(true);
    actions.dispatch('n-garden', 'Add a plant');
    expect(actions.activeForm()).toBeNull();
    expect(store.announcement()).toContain('queued');
  });

  it('"Identify a plant" opens the IN-ATLAS identify form (no redirect)', () => {
    actions.dispatch('n-ident', 'Identify a plant');
    expect(actions.activeForm()).toEqual({ kind: 'identify' });
    actions.dispatch('n-species', 'Add a species by hand');
    expect(actions.activeForm()).toEqual({ kind: 'identify' });
  });

  it('identify() POSTs multipart images+organs and requests a reload', () => {
    const file = new File(['x'], 'leaf.jpg', { type: 'image/jpeg' });
    actions.identify([file], 'leaf', 'brown spots');
    const req = http.expectOne('/api/v1/identifications/analyze');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect((body.get('images') as File).name).toBe('leaf.jpg');
    expect(body.get('organs')).toBe('leaf');
    expect(body.get('userContext')).toBe('brown spots');
    req.flush({ success: true, message: '', timestamp: '', data: { identificationId: 1 } });
    expect(actions.activeForm()).toBeNull();
    expect(actions.reloadRequested()).toBe(1);
  });

  it('care-loop actions defer honestly (no fake success)', () => {
    actions.dispatch('n-plant-7', 'Water plant');
    expect(store.announcement()).toContain('care loop');
  });
});
