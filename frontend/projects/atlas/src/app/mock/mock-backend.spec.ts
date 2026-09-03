import { TestBed } from '@angular/core/testing';
import { provideMockModeOff } from '../core/mock-mode';
import { derivePlant } from './mock.dataset';
import { MockBackend, MockReply } from './mock-backend';

const NOW = Date.parse('2026-09-03T09:12:00Z');
const DAY = 86400000;

function data(reply: MockReply): Record<string, unknown> {
  return (reply.body as { data: Record<string, unknown> }).data;
}
function list<T>(reply: MockReply): T[] {
  return (reply.body as { data: T[] }).data;
}
function content<T>(reply: MockReply): T[] {
  return (reply.body as { data: { content: T[] } }).data.content;
}
function message(reply: MockReply): string {
  return (reply.body as { message: string }).message;
}
function hasNull(value: unknown): boolean {
  if (value === null) return true;
  if (Array.isArray(value)) return value.some(hasNull);
  if (value && typeof value === 'object') return Object.values(value as Record<string, unknown>).some(hasNull);
  return false;
}

describe('MockBackend (S1 — the in-memory PlantPal)', () => {
  let b: MockBackend;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(NOW);
    TestBed.configureTestingModule({ providers: [provideMockModeOff()] });
    b = TestBed.inject(MockBackend);
    b.reset('garden', NOW);
  });
  afterEach(() => jest.useRealTimers());

  it('GET /plants returns a page and derives nextWaterDays', () => {
    const reply = b.handle('GET', '/plants?size=50', null);
    expect(reply.status).toBe(200);
    const plants = content<{ id: number; nextWaterDays?: number }>(reply);
    expect(plants).toHaveLength(6);
    expect(plants.find(p => p.id === 1)!.nextWaterDays).toBe(-2);
    expect(plants.find(p => p.id === 4)).not.toHaveProperty('nextWaterDays');
    expect(plants.find(p => p.id === 4)).not.toHaveProperty('healthStatus');
  });

  it('GET /reminders returns an enabled-only array sorted by nextDueAt', () => {
    const rows = list<{ id: number; nextDueAt: string; enabled: boolean }>(b.handle('GET', '/reminders', null));
    expect(Array.isArray(rows)).toBe(true);
    expect(rows.every(r => r.enabled)).toBe(true);
    expect(rows.map(r => r.id)).not.toContain(701);
    const times = rows.map(r => Date.parse(r.nextDueAt));
    expect(times).toEqual([...times].sort((x, y) => x - y));
  });

  it('POST /care/done on 601 advances nextDueAt by seven days and appends a care log', () => {
    const reply = b.handle('POST', '/care/done', { reminderId: 601, notes: 'Full soak' });
    expect(reply.status).toBe(201);
    expect(message(reply)).toBe('Care logged successfully');
    expect(data(reply)['notes']).toBe('Full soak');
    const r = b.state.reminders.find(x => x.id === 601)!;
    expect(Date.parse(r.nextDueAt)).toBe(NOW + 7 * DAY);
    expect(r.enabled).toBe(true);
    expect(b.state.careLogs.filter(l => l.plantId === 1)).toHaveLength(3);
  });

  it('POST /care/done on step 702 disables it and derives completedAt', () => {
    b.handle('POST', '/care/done', { reminderId: 702 });
    expect(b.state.reminders.find(r => r.id === 702)!.enabled).toBe(false);
    const plan = data(b.handle('GET', '/treatment-plans/201', null));
    const step = (plan['steps'] as { id: number; completedAt?: string }[]).find(s => s.id === 702)!;
    expect(step.completedAt).toBe(new Date(NOW).toISOString());
  });

  it('completing 702, 703 and 704 completes plan 201, treatment 301 and clears the plant pointer', () => {
    for (const id of [702, 703, 704]) b.handle('POST', '/care/done', { reminderId: id });
    expect(b.state.treatmentPlans.find(p => p.id === 201)!.status).toBe('COMPLETED');
    expect(b.state.treatments.find(t => t.id === 301)!.status).toBe('COMPLETED');
    expect(derivePlant(b.state, b.state.plants[0], NOW).activeTreatmentId).toBeUndefined();
  });

  it('a second completion of 701 answers 400', () => {
    const reply = b.handle('POST', '/care/done', { reminderId: 701 });
    expect(reply.status).toBe(400);
    expect(message(reply)).toBe('This reminder has already been completed');
  });

  it('DELETE /reminders/603 answers 204 with a null body and removes it from the list', () => {
    const reply = b.handle('DELETE', '/reminders/603', null);
    expect(reply).toEqual({ status: 204, body: null });
    expect(list<{ id: number }>(b.handle('GET', '/reminders', null)).map(r => r.id)).not.toContain(603);
  });

  it('POST /treatments rejects a duplicate active disease', () => {
    const reply = b.handle('POST', '/treatments', { plantId: 1, diseaseName: 'Root rot' });
    expect(reply.status).toBe(400);
    expect(message(reply)).toBe('An active treatment already exists for this plant and disease');
  });

  it('craft-plan on 303 answers 429 with retryAfterSeconds, then succeeds with four steps', () => {
    const limited = b.handle('POST', '/treatments/303/craft-plan', null);
    expect(limited.status).toBe(429);
    expect((limited.body as { retryAfterSeconds: number }).retryAfterSeconds).toBe(900);

    const ok = b.handle('POST', '/treatments/303/craft-plan', null);
    expect(ok.status).toBe(200);
    const t = b.state.treatments.find(x => x.id === 303)!;
    expect(t.status).toBe('IN_PROGRESS');
    expect(b.state.reminders.filter(r => r.treatmentPlanId === t.treatmentPlanId)).toHaveLength(4);
  });

  it('craft-plan on 301 answers 400 because it is not a DRAFT', () => {
    const reply = b.handle('POST', '/treatments/301/craft-plan', null);
    expect(reply.status).toBe(400);
    expect(message(reply)).toBe('Treatment plan can only be crafted from DRAFT status');
  });

  it('regenerate-description on 303 answers 202, then READY on the second read', () => {
    expect(b.handle('POST', '/treatments/303/regenerate-description', null).status).toBe(202);
    expect(data(b.handle('GET', '/treatments/303', null))['descriptionStatus']).toBe('PENDING');
    expect(data(b.handle('GET', '/treatments/303', null))['descriptionStatus']).toBe('READY');
  });

  it('identification 505 flips to COMPLETED on the second list read', () => {
    const first = content<{ id: number; status: string }>(b.handle('GET', '/identifications?size=50', null));
    expect(first.find(i => i.id === 505)!.status).toBe('PENDING');
    const second = content<{ id: number; status: string }>(b.handle('GET', '/identifications?size=50', null));
    expect(second.find(i => i.id === 505)!.status).toBe('COMPLETED');
  });

  it('retry moves 503 from FAILED to PENDING', () => {
    expect(b.handle('POST', '/identifications/503/retry', null).status).toBe(200);
    expect(b.state.identifications.find(i => i.id === 503)!.status).toBe('PENDING');
  });

  it('GET /plants/4/active-treatment answers 404 and /active-treatments an empty array', () => {
    const one = b.handle('GET', '/plants/4/active-treatment', null);
    expect(one.status).toBe(404);
    expect(message(one)).toBe('No active treatment for this plant');
    expect(list(b.handle('GET', '/plants/4/active-treatments', null))).toEqual([]);
  });

  it('GET /dashboard buckets today and overdue, with five species and three recent scans', () => {
    const d = data(b.handle('GET', '/dashboard', null));
    expect((d['overdueReminders'] as { id: number }[]).map(r => r.id).sort()).toEqual([601, 606]);
    expect((d['todayReminders'] as { id: number }[]).map(r => r.id).sort()).toEqual([602, 702]);
    expect(d['speciesCount']).toBe(5);
    expect((d['recentScans'] as unknown[]).length).toBe(3);
    expect(d['healthSummary']).toEqual({ healthy: 3, issues: 2, unknown: 1 });
  });

  it('PUT /users/me/preferences merges only the keys it is given', () => {
    const reply = b.handle('PUT', '/users/me/preferences', { businessTier: true });
    expect(reply.status).toBe(200);
    expect(data(reply)['businessTier']).toBe(true);
    expect(data(reply)['reasoningModelPreference']).toBe('DEEPSEEK_R1');
  });

  it('an unrouted path answers 404 with a loud message', () => {
    const reply = b.handle('GET', '/nowhere', null);
    expect(reply.status).toBe(404);
    expect(message(reply)).toBe('No such place in the mock backend: GET /nowhere');
  });

  it('the outage scenario answers 503 for reminders, dashboard and treatment-plans/201', () => {
    b.reset('outage', NOW);
    for (const path of ['/reminders', '/dashboard', '/treatment-plans/201']) {
      const reply = b.handle('GET', path, null);
      expect(reply.status).toBe(503);
      expect(message(reply)).toBe('Service unavailable');
    }
    expect(b.handle('GET', '/plants?size=50', null).status).toBe(200);
  });

  it('failNext makes exactly one following mutation answer 503', () => {
    b.failNext = true;
    expect(b.handle('GET', '/plants', null).status).toBe(200);
    const failed = b.handle('POST', '/care/done', { reminderId: 601 });
    expect(failed.status).toBe(503);
    expect(message(failed)).toContain('Nothing was changed');
    expect(b.handle('POST', '/care/done', { reminderId: 601 }).status).toBe(201);
  });

  it('no response body contains a null value', () => {
    const paths: [string, string][] = [
      ['GET', '/plants?size=50'], ['GET', '/species/mine?size=50'], ['GET', '/identifications?size=50'],
      ['GET', '/reminders'], ['GET', '/dashboard'], ['GET', '/treatment-plans/201'],
      ['GET', '/treatments/301'], ['GET', '/care/plant/1?size=5'], ['GET', '/users/me/preferences'],
    ];
    for (const [method, path] of paths) {
      expect([path, hasNull(b.handle(method, path, null).body)]).toEqual([path, false]);
    }
  });
});
