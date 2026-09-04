import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { provideMockModeOff } from '../core/mock-mode';
import { DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import { WorldActionsService } from './world-actions.service';
import type { ReminderDto } from './world.dto';
import type { WorldMeta } from './world.model';
import { WorldStore } from './world.store';

const HOUR = 3_600_000;
const DAY = 86_400_000;

function reminder(over: Partial<ReminderDto> & { id: number; plantId: number }): ReminderDto {
  return {
    careType: 'WATERING',
    frequencyDays: 7,
    nextDueAt: new Date(Date.now() + DAY).toISOString(),
    enabled: true,
    recurring: true,
    ...over,
  };
}

/** 601 overdue water on plant 1, 602 water due today on plant 2, 702 an open step. */
function meta(): WorldMeta {
  const now = new Date();
  const today18 = new Date(now);
  today18.setHours(18, 0, 0, 0);
  return {
    syncedAt: now.toISOString(),
    reminders: [
      reminder({ id: 601, plantId: 1, nextDueAt: new Date(Date.now() - 2 * DAY).toISOString() }),
      reminder({ id: 602, plantId: 2, nextDueAt: today18.toISOString() }),
      reminder({ id: 606, plantId: 1, careType: 'FERTILIZING', nextDueAt: new Date(Date.now() + 9 * DAY).toISOString() }),
      reminder({
        id: 702,
        plantId: 1,
        recurring: false,
        frequencyDays: 0,
        careType: 'PEST',
        treatmentPlanId: 201,
        stepOrder: 2,
        nextDueAt: new Date(Date.now() - HOUR).toISOString(),
      }),
    ],
    dueReminders: [],
    plantsIndex: [
      { id: 1, nickname: 'Office Fig' },
      { id: 2, nickname: 'Studio Fig' },
      { id: 3, nickname: 'Hallway Pothos' },
    ],
    treatmentsIndex: {
      301: { plantId: 1, status: 'IN_PROGRESS', planId: 201, nextStepId: 702, nextStepOrder: 2, paused: false },
      303: { plantId: 5, status: 'DRAFT', paused: false },
    },
    scansByPlant: { 1: 501 },
    hasPendingDescription: false,
    failures: [],
  };
}

describe('WorldActionsService (H6 — every button works as intended)', () => {
  let actions: WorldActionsService;
  let store: WorldStore;
  let settings: SettingsStore;
  let device: DeviceStore;
  let http: HttpTestingController;

  const ok = (data: unknown = {}) => ({ success: true, message: '', timestamp: '', data });

  function withMeta(): void {
    (store as unknown as { data: { update: (fn: (d: object) => object) => void } }).data.update(
      d => ({ ...d, meta: meta() }),
    );
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideMockModeOff(), ...provideSharedCore({ apiBaseUrl: '/api/v1' })],
    });
    actions = TestBed.inject(WorldActionsService);
    store = TestBed.inject(WorldStore);
    settings = TestBed.inject(SettingsStore);
    device = TestBed.inject(DeviceStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  // ── round 1 (unchanged behaviour) ─────────────────────────────────────────

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
    req.flush(ok({ id: 9, nickname: 'New Fig' }));
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
    req.flush(ok());
    expect(store.announcement()).toContain('camera did not move');
  });

  it('"Try the scan again" POSTs the retry for the latest failed scan', () => {
    (store as unknown as { data: { update: (fn: (d: object) => object) => void } }).data
      .update(d => ({ ...d, latestFailedScanId: 42 }));
    actions.dispatch('n-ident', 'Try the scan again');
    const retry = http.expectOne('/api/v1/identifications/42/retry');
    expect(retry.request.method).toBe('POST');
    retry.flush(ok());
    expect(actions.reloadRequested()).toBe(1);
  });

  it('"Check health again" runs a real timed call and reports UP', () => {
    actions.dispatch('n-platform', 'Check health again');
    http.expectOne(r => r.url === '/api/v1/plants').flush(ok());
    expect(store.announcement()).toMatch(/UP/);
  });

  it('offline queues everything and fires no request', () => {
    store.probeOffline.set(true);
    actions.dispatch('n-plant-1', 'Water plant', 'plant:1');
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
    req.flush(ok({ identificationId: 1 }));
    expect(actions.activeForm()).toBeNull();
    expect(actions.reloadRequested()).toBe(1);
  });

  // ── round 2: care ─────────────────────────────────────────────────────────

  it('Water plant posts to care done for the plants watering reminder', () => {
    withMeta();
    actions.dispatch('n-plant-1', 'Water plant', 'plant:1');
    const req = http.expectOne('/api/v1/care/done');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reminderId: 601 });
    req.flush(ok({ id: 1 }));
    expect(store.announcement()).toBe('Watered. The camera did not move.');
    expect(actions.reloadRequested()).toBe(1);
  });

  it('with the reminders complete verb it posts to reminders complete instead', () => {
    withMeta();
    settings.set('care.completeVerb', 'reminders/complete');
    actions.dispatch('n-plant-1', 'Water plant', 'plant:1');
    const req = http.expectOne('/api/v1/reminders/601/complete');
    expect(req.request.method).toBe('POST');
    req.flush(ok({ id: 601 }));
    expect(store.announcement()).toContain('Watered');
  });

  it('with no schedule and create-schedule it posts a reminder then completes it', () => {
    withMeta();
    actions.dispatch('n-plant-3', 'Water plant', 'plant:3');
    const create = http.expectOne('/api/v1/reminders');
    expect(create.request.body.plantId).toBe(3);
    expect(create.request.body.careType).toBe('WATERING');
    expect(create.request.body.frequencyDays).toBe(7);
    create.flush(ok({ id: 900 }));
    const done = http.expectOne('/api/v1/care/done');
    expect(done.request.body.reminderId).toBe(900);
    done.flush(ok({ id: 2 }));
    expect(store.announcement()).toContain('Watered');
  });

  it('with no schedule and refuse it only announces', () => {
    withMeta();
    settings.set('care.logWithoutReminder', 'refuse');
    actions.dispatch('n-plant-3', 'Water plant', 'plant:3');
    http.expectNone(() => true);
    expect(store.announcement()).toContain('Hallway Pothos');
    expect(actions.activeForm()).toMatchObject({ kind: 'add-reminder', plantId: 3 });
  });

  it('a 400 already completed counts as success and reloads', () => {
    withMeta();
    actions.dispatch('n-plant-1', 'Water plant', 'plant:1');
    http
      .expectOne('/api/v1/care/done')
      .flush({ message: 'This reminder has already been completed' }, { status: 400, statusText: 'Bad' });
    expect(store.announcement()).toContain('Already done');
    expect(actions.reloadRequested()).toBe(1);
  });

  it('Mark step 2 as done posts for the step named by data-arg', () => {
    withMeta();
    actions.dispatch('n-treatment-301', 'Mark step 2 as done', 'reminder:702');
    const req = http.expectOne('/api/v1/care/done');
    expect(req.request.body.reminderId).toBe(702);
    req.flush(ok({ id: 3 }));
    expect(actions.reloadRequested()).toBe(1);
  });

  it('Mark today done resolves the first open step from the meta index', () => {
    withMeta();
    actions.dispatch('n-treatment-301', 'Mark today done');
    const req = http.expectOne('/api/v1/care/done');
    expect(req.request.body.reminderId).toBe(702);
    req.flush(ok({ id: 4 }));
  });

  it('Water all completes every due watering reminder and counts them', () => {
    withMeta();
    actions.dispatch('n-garden', 'Water all');
    const reqs = http.match('/api/v1/care/done');
    expect(reqs.length).toBe(2);
    expect(reqs.map(r => r.request.body.reminderId).sort()).toEqual([601, 602]);
    reqs.forEach(r => r.flush(ok({ id: 1 })));
    expect(store.announcement()).toBe('Watered 2 plants. The camera did not move.');
  });

  it('Water all with nothing due says so and makes no request', () => {
    (store as unknown as { data: { update: (fn: (d: object) => object) => void } }).data.update(d => ({
      ...d,
      meta: { ...meta(), reminders: [] },
    }));
    actions.dispatch('n-garden', 'Water all');
    http.expectNone(() => true);
    expect(store.announcement()).toBe('Nothing is due for water.');
  });

  it('Change the schedule posts the new reminder then deletes the old one and tolerates a 204 with a null body', () => {
    withMeta();
    actions.dispatch('n-care', 'Change the schedule', 'reminder:601');
    expect(actions.activeForm()).toMatchObject({ kind: 'change-schedule', reminderId: 601, plantId: 1, frequencyDays: 7 });
    actions.changeSchedule(601, {
      plantId: 1,
      careType: 'WATERING',
      frequencyDays: 10,
      firstDueAt: new Date().toISOString(),
    });
    const create = http.expectOne('/api/v1/reminders');
    expect(create.request.body.frequencyDays).toBe(10);
    create.flush(ok({ id: 901 }));
    const del = http.expectOne('/api/v1/reminders/601');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    expect(store.announcement()).toContain('schedule is changed');
    expect(actions.activeForm()).toBeNull();
  });

  it('a failed retire says the new schedule stands and the old one must be retired', () => {
    actions.changeSchedule(601, {
      plantId: 1,
      careType: 'WATERING',
      frequencyDays: 10,
      firstDueAt: new Date().toISOString(),
    });
    http.expectOne('/api/v1/reminders').flush(ok({ id: 901 }));
    http.expectOne('/api/v1/reminders/601').flush(null, { status: 500, statusText: 'Server' });
    expect(store.announcement()).toContain('still there');
  });

  it('Stop this reminder deletes the row and says it stays readable', () => {
    withMeta();
    actions.dispatch('n-reminders', 'Stop this reminder', 'reminder:601');
    const del = http.expectOne('/api/v1/reminders/601');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    expect(store.announcement()).toContain('stays readable');
    expect(actions.reloadRequested()).toBe(1);
  });

  it('Snooze until tomorrow writes device state and makes no request', () => {
    withMeta();
    actions.dispatch('n-reminders', 'Snooze the overdue one', 'reminder:601');
    http.expectNone(() => true);
    expect(device.care('live').snoozed[601]).toBeDefined();
    expect(store.announcement()).toContain('on this device');
  });

  it('with snooze off it refuses in words', () => {
    withMeta();
    settings.set('reminders.snooze', 'off');
    actions.dispatch('n-reminders', 'Snooze the overdue one', 'reminder:601');
    http.expectNone(() => true);
    expect(device.care('live').snoozed[601]).toBeUndefined();
    expect(store.announcement()).toContain('not something PlantPal keeps yet');
  });

  // ── round 2: treatments ───────────────────────────────────────────────────

  it('Start a treatment plan opens the sheet with the plants latest scan', () => {
    withMeta();
    actions.dispatch('n-plant-1', 'Start a treatment plan', 'plant:1');
    expect(actions.activeForm()).toEqual({
      kind: 'start-treatment',
      plantId: 1,
      plantName: 'Office Fig',
      identificationId: 501,
    });
  });

  it('startTreatment posts and chains craft-plan when the setting is on', () => {
    settings.set('ai.craftPlanOnStart', true);
    actions.startTreatment({ plantId: 1, diseaseName: 'Root rot', identificationId: 501 });
    const create = http.expectOne('/api/v1/treatments');
    expect(create.request.body.diseaseName).toBe('Root rot');
    create.flush(ok({ id: 310 }));
    http.expectOne('/api/v1/treatments/310/craft-plan').flush(ok({ id: 310 }));
    expect(store.announcement()).toContain('steps');
  });

  it('startTreatment leaves the draft alone when the setting is off', () => {
    actions.startTreatment({ plantId: 1, diseaseName: 'Root rot' });
    http.expectOne('/api/v1/treatments').flush(ok({ id: 311 }));
    http.expectNone('/api/v1/treatments/311/craft-plan');
    expect(store.announcement()).toContain('draft');
  });

  it('craft-plan 429 records the limit and announces the minutes', () => {
    actions.dispatch('n-treatment-303', 'Craft the treatment plan', 'treatment:303');
    http
      .expectOne('/api/v1/treatments/303/craft-plan')
      .flush({ message: 'AI rate limit reached', retryAfterSeconds: 900 }, { status: 429, statusText: 'Too Many' });
    expect(actions.rateLimited()[303].retryAfterSeconds).toBe(900);
    expect(store.announcement()).toContain('15 minutes');
  });

  it('Pause this course toggles device state without HTTP', () => {
    withMeta();
    actions.dispatch('n-treatment-301', 'Pause this course', 'plan:201');
    http.expectNone(() => true);
    expect(device.care('live').pausedPlanIds).toEqual([201]);
    actions.dispatch('n-treatment-301', 'Resume this course', 'plan:201');
    expect(device.care('live').pausedPlanIds).toEqual([]);
    expect(store.announcement()).toContain('Resumed');
  });

  it('Write it up again posts regenerate-description', () => {
    actions.dispatch('n-treatment-301', 'Write it up again', 'treatment:301');
    const req = http.expectOne('/api/v1/treatments/301/regenerate-description');
    expect(req.request.method).toBe('POST');
    req.flush(ok({ id: 301 }));
    expect(store.announcement()).toContain('arrives into this node');
  });

  it('Finish this course patches complete', () => {
    actions.dispatch('n-treatment-301', 'Finish this course', 'treatment:301');
    const req = http.expectOne('/api/v1/treatments/301/complete');
    expect(req.request.method).toBe('PATCH');
    req.flush(ok({ id: 301 }));
    expect(store.announcement()).toContain('finished');
  });

  it('Add the steps by hand opens an add-reminder sheet on the treated plant', () => {
    withMeta();
    actions.dispatch('n-treatment-301', 'Add the steps by hand', 'treatment:301');
    expect(actions.activeForm()).toEqual({
      kind: 'add-reminder',
      plantId: 1,
      plantName: 'Office Fig',
      careType: 'PEST',
    });
  });

  it('an unknown label says PlantPal cannot do it from here yet', () => {
    actions.dispatch('n-ask', 'Ask about my garden');
    expect(store.announcement()).toBe('“Ask about my garden” is not something PlantPal can do from here yet.');
  });

  it('every successful path leaves focusId and camera unchanged', () => {
    withMeta();
    const focus = store.focusId();
    const camera = { ...store.camera() };
    actions.dispatch('n-plant-1', 'Water plant', 'plant:1');
    http.expectOne('/api/v1/care/done').flush(ok({ id: 1 }));
    actions.dispatch('n-treatment-301', 'Pause this course', 'plan:201');
    expect(store.focusId()).toBe(focus);
    expect(store.camera()).toEqual(camera);
  });
});
