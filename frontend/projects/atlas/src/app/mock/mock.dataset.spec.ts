import { buildMockSeed, MockSeed } from './mock.dataset';

const NOW = Date.parse('2026-09-03T09:12:00Z');

function hasNull(value: unknown): boolean {
  if (value === null) return true;
  if (Array.isArray(value)) return value.some(hasNull);
  if (value && typeof value === 'object' && !(value instanceof RegExp)) {
    return Object.values(value as Record<string, unknown>).some(hasNull);
  }
  return false;
}

describe('buildMockSeed (S1 — the mock garden dataset)', () => {
  let seed: MockSeed;
  beforeEach(() => (seed = buildMockSeed('garden', NOW)));

  it('is deterministic for a fixed now', () => {
    expect(buildMockSeed('garden', NOW)).toEqual(buildMockSeed('garden', NOW));
  });

  it('draws at least four members in every collapsing family', () => {
    expect(seed.plants.length).toBeGreaterThanOrEqual(4);
    expect(seed.species.length).toBeGreaterThanOrEqual(4);
    expect(seed.identifications.length).toBeGreaterThanOrEqual(4);
    expect(seed.reminders.filter(r => r.recurring).length).toBeGreaterThanOrEqual(4);
    expect(seed.treatments.length).toBeGreaterThanOrEqual(4);
    expect(seed.careLogs.length).toBeGreaterThanOrEqual(4);
  });

  it('has exactly one PENDING and one FAILED identification', () => {
    expect(seed.identifications.filter(i => i.status === 'PENDING')).toHaveLength(1);
    expect(seed.identifications.filter(i => i.status === 'FAILED')).toHaveLength(1);
  });

  it('gives plan 201 exactly one step due today and one already done', () => {
    const steps = seed.reminders.filter(r => r.treatmentPlanId === 201);
    expect(steps).toHaveLength(4);
    expect(steps.filter(s => !s.enabled)).toHaveLength(1);
    const startOfDay = new Date(NOW);
    startOfDay.setHours(0, 0, 0, 0);
    const dueToday = steps.filter(
      s => s.enabled && Date.parse(s.nextDueAt) >= startOfDay.getTime() && Date.parse(s.nextDueAt) < startOfDay.getTime() + 86400000,
    );
    expect(dueToday).toHaveLength(1);
    expect(dueToday[0].id).toBe(702);
  });

  it('contains no null values anywhere', () => {
    expect(hasNull(seed)).toBe(false);
  });

  it('day-zero has a user and nothing else', () => {
    const zero = buildMockSeed('day-zero', NOW);
    expect(zero.user.firstName).toBe('Sam');
    expect(zero.plants).toEqual([]);
    expect(zero.species).toEqual([]);
    expect(zero.identifications).toEqual([]);
    expect(zero.reminders).toEqual([]);
    expect(zero.careLogs).toEqual([]);
    expect(zero.treatments).toEqual([]);
    expect(zero.treatmentPlans).toEqual([]);
  });

  it('outage marks reminders, dashboard, treatment-plans/201 and chat as failing', () => {
    const outage = buildMockSeed('outage', NOW);
    expect(outage.plants).toHaveLength(6);
    const failing = outage.failing.map(f => `${f.method} ${f.re.source}`);
    expect(failing).toEqual([
      'GET ^\\/reminders$',
      'GET ^\\/dashboard$',
      'GET ^\\/treatment-plans\\/201$',
      'POST ^\\/chat(\\/stream)?$',
    ]);
  });
});
