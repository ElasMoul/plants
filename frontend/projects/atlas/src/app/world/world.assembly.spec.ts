import { assembleWorld } from './world.assembly';
import {
  CareLogDto,
  DashboardDto,
  emptySources,
  IdentificationDto,
  PlantDto,
  ReminderDto,
  SpeciesDto,
  TreatmentDto,
  TreatmentPlanDto,
  WorldSources,
} from './world.dto';

function plant(id: number, over: Partial<PlantDto> = {}): PlantDto {
  return { id, nickname: `Plant ${id}`, species: 'Ficus lyrata', commonName: 'Fig', nextWaterDays: 5, healthStatus: 'HEALTHY', ...over };
}
function species(id: number): SpeciesDto {
  return { id, scientificName: `Genus sp${id}`, commonName: `Common ${id}` };
}
function ident(id: number, status = 'COMPLETED', over: Partial<IdentificationDto> = {}): IdentificationDto {
  return { id, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: 'HEALTHY', status, createdAt: `2026-08-0${id}T10:00:00Z`, ...over };
}

function sources(over: Partial<WorldSources> = {}): WorldSources {
  return emptySources({
    now: '2026-09-03T09:12:00Z',
    plants: [plant(1), plant(2), plant(3)],
    species: [species(1), species(2)],
    identifications: [ident(1)],
    user: { firstName: 'Mo', lastName: 'El', email: 'mo@example.com' },
    ...over,
  });
}

const idsOf = (w: ReturnType<typeof assembleWorld>) => w.nodes.map(n => n.id);

describe('assembleWorld (H5 — the live round-1 spine)', () => {
  it('builds the garden hub as the initial focus with the spine nodes', () => {
    const w = assembleWorld(sources());
    expect(w.initialFocus).toBe('n-garden');
    expect(idsOf(w)).toEqual(expect.arrayContaining(['n-garden', 'n-account', 'n-platform', 'n-ident', 'n-species', 'n-reminders', 'n-care']));
  });

  it('recaps the garden from real plant data', () => {
    const w = assembleWorld(sources({ plants: [plant(1, { nextWaterDays: 0 }), plant(2)] }));
    expect(w.nodes.find(n => n.id === 'n-garden')!.recap).toBe('2 plants · 1 need water');
  });

  it('shows the signed-in user on the account node', () => {
    const acc = assembleWorld(sources()).nodes.find(n => n.id === 'n-account')!;
    expect(acc.name).toBe("Mo's account");
    expect(acc.recap).toBe('mo@example.com');
    expect(acc.body).toContain('mo@example.com');
  });

  it('escapes user-originated text in generated bodies', () => {
    const w = assembleWorld(sources({ plants: [plant(1, { nickname: '<img src=x onerror=alert(1)>' })] }));
    const p = w.nodes.find(n => n.id === 'n-plant-1')!;
    expect(p.body).not.toContain('<img');
    expect(p.body).toContain('&lt;img');
  });

  describe('identifications (the async family)', () => {
    it('marks the ident node failed when the latest scan failed', () => {
      const w = assembleWorld(sources({ identifications: [ident(2, 'FAILED'), ident(1)] }));
      const n = w.nodes.find(x => x.id === 'n-ident')!;
      expect(n.state).toBe('failed');
      expect(n.body).toContain('Try the scan again');
    });
    it('sets hasPendingScan while a scan is analysing', () => {
      expect(assembleWorld(sources({ identifications: [ident(2, 'PENDING')] })).hasPendingScan).toBe(true);
      expect(assembleWorld(sources()).hasPendingScan).toBe(false);
    });
    it('links the identify → species path', () => {
      const w = assembleWorld(sources());
      expect(w.edges).toEqual(expect.arrayContaining([['n-ident', 'n-species']]));
    });
  });

  describe('density collapse (C4)', () => {
    it('draws all plants when fewer than four', () => {
      const w = assembleWorld(sources());
      expect(w.nodes.filter(n => n.id.startsWith('n-plant-'))).toHaveLength(3);
      expect(idsOf(w).includes('n-garden-more')).toBe(false);
    });
    it('draws two + "+N more" when four or more', () => {
      const w = assembleWorld(sources({ plants: [plant(1), plant(2), plant(3), plant(4), plant(5)] }));
      expect(w.nodes.filter(n => n.id.startsWith('n-plant-'))).toHaveLength(2);
      expect(w.nodes.find(n => n.id === 'n-garden-more')!.recap).toBe('+3 more');
    });
    it('ranks issue plants first', () => {
      const many = [plant(1, { nextWaterDays: 10 }), plant(2, { healthStatus: 'ISSUES_DETECTED' }), plant(3), plant(4)];
      const w = assembleWorld(sources({ plants: many }));
      expect(idsOf(w)).toContain('n-plant-2');
    });
  });

  describe('deferred families + problems', () => {
    it('leaves only the companion deferred — the day is real now', () => {
      const w = assembleWorld(sources());
      expect(w.nodes.find(n => n.id === 'n-today')!.body).toContain("Today's summary");
      expect(w.nodes.find(n => n.id === 'n-today')!.body).not.toContain('round 3');
      expect(w.nodes.find(n => n.id === 'n-ask')!.state).toBe('empty');
    });
    it('adds a Problems node only when plants need attention', () => {
      expect(idsOf(assembleWorld(sources())).includes('n-problems')).toBe(false);
      const w = assembleWorld(sources({ plants: [plant(1, { healthStatus: 'ISSUES_DETECTED' })] }));
      expect(idsOf(w).includes('n-problems')).toBe(true);
    });
  });

  describe('the tour (H9): scans are nodes, rows navigate', () => {
    it('draws each identification as a node linked to its plant', () => {
      const w = assembleWorld(sources({ identifications: [
        { id: 5, species: 'Ficus lyrata', commonName: 'Fig', healthStatus: null, status: 'COMPLETED', createdAt: '2026-08-01T10:00:00Z', plantId: 1 },
      ] }));
      const scan = w.nodes.find(n => n.id === 'n-scan-5')!;
      expect(scan).toBeDefined();
      expect(scan.kindLabel).toBe('Scan');
      expect(w.edges).toEqual(expect.arrayContaining([['n-ident', 'n-scan-5'], ['n-scan-5', 'n-plant-1']]));
      expect(scan.body).toContain('data-goto="n-plant-1"');
    });
    it('garden rows doc-link to drawn plant nodes; page nodes exist', () => {
      const w = assembleWorld(sources());
      const garden = w.nodes.find(n => n.id === 'n-garden')!;
      expect(garden.body).toContain('data-goto="n-plant-1"');
      for (const id of ['n-ask', 'n-today', 'n-treatments']) {
        expect(w.nodes.some(n => n.id === id)).toBe(true);
      }
    });
  });

  describe('per-family failure (C25)', () => {
    it('renders a family failure as a failed state inside its own hub', () => {
      const w = assembleWorld(
        sources({ failures: [{ family: 'reminders', status: 503, at: '2026-09-03T09:12:00Z' }] }),
      );
      const n = w.nodes.find(x => x.id === 'n-reminders')!;
      expect(n.state).toBe('failed');
      expect(n.body).toContain('state--error');
      expect(n.body).toContain('Fetch this region');
      expect(n.body).toContain('nothing moved');
      expect(n.failure!.waysForward).toEqual(['Fetch this region']);
      // the rest of the board is untouched — degradation is per-node material
      expect(w.nodes.find(x => x.id === 'n-care')!.state).toBeUndefined();
      expect(w.nodes.find(x => x.id === 'n-garden')!.state).toBeUndefined();
    });

    it('offers the dashboard a second way through', () => {
      const w = assembleWorld(
        sources({ failures: [{ family: 'dashboard', status: 500, at: '2026-09-03T09:12:00Z' }] }),
      );
      const n = w.nodes.find(x => x.id === 'n-today')!;
      expect(n.failure!.waysForward).toEqual(['Fetch this region', 'Count again']);
      expect(n.body).toContain('Count again');
    });
  });

  describe('meta — the loader facts beside the board', () => {
    it('lists every plant and every due reminder', () => {
      const w = assembleWorld(
        sources({
          plants: [plant(1), plant(2)],
          reminders: [
            { id: 601, plantId: 1, plantNickname: 'Plant 1', careType: 'WATERING', frequencyDays: 7, nextDueAt: '2026-09-01T08:00:00Z', enabled: true, recurring: true },
            { id: 602, plantId: 2, plantNickname: 'Plant 2', careType: 'FERTILIZING', frequencyDays: 30, nextDueAt: '2026-10-20T08:00:00Z', enabled: true, recurring: true },
          ],
        }),
      );
      expect(w.meta!.syncedAt).toBe('2026-09-03T09:12:00Z');
      expect(w.meta!.plantsIndex.map(p => p.id)).toEqual([1, 2]);
      expect(w.meta!.dueReminders).toEqual([
        { id: 601, plantId: 1, nextDueAt: '2026-09-01T08:00:00Z', label: 'Water · Plant 1' },
      ]);
      expect(w.meta!.hasPendingDescription).toBe(false);
    });

    it('flags a disease description still being written', () => {
      const w = assembleWorld(
        sources({
          treatments: [{ id: 301, plantId: 1, diseaseName: 'Root rot', status: 'DRAFT', descriptionStatus: 'PENDING', createdAt: '2026-09-01T09:00:00Z' }],
        }),
      );
      expect(w.meta!.hasPendingDescription).toBe(true);
      expect(w.meta!.treatmentsIndex[301].plantId).toBe(1);
    });

    it('polls for no description a course will never write', () => {
      const base = { id: 301, plantId: 1, diseaseName: 'Root rot', createdAt: '2026-09-01T09:00:00Z' };
      // no status at all is not a promise of one arriving
      expect(
        assembleWorld(sources({ treatments: [{ ...base, status: 'IN_PROGRESS' }] })).meta!
          .hasPendingDescription,
      ).toBe(false);
      // a dismissed course is finished, pending or not
      expect(
        assembleWorld(
          sources({ treatments: [{ ...base, status: 'DISMISSED', descriptionStatus: 'PENDING' }] }),
        ).meta!.hasPendingDescription,
      ).toBe(false);
    });
  });

  describe('insertion stability (C8)', () => {
    it('is identical to the centred layout when no prior cells are given', () => {
      const cells = Object.fromEntries(assembleWorld(sources()).nodes.map(n => [n.id, n.cell]));
      expect(cells['n-garden']).toEqual({ col: 0, row: 6 });
      expect(Object.fromEntries(assembleWorld(sources()).nodes.map(n => [n.id, n.cell]))).toEqual(cells);
    });

    it('keeps every prior cell and gives a new node a free one', () => {
      const before = assembleWorld(sources());
      const priorCells = Object.fromEntries(before.nodes.map(n => [n.id, n.cell]));
      const after = assembleWorld(
        sources({ plants: [plant(1), plant(2), plant(3)], species: [species(1), species(2), species(3)], priorCells }),
      );
      for (const n of before.nodes) {
        const moved = after.nodes.find(x => x.id === n.id);
        if (moved) expect(moved.cell).toEqual(n.cell);
      }
      const fresh = after.nodes.find(n => n.id === 'n-species-3')!;
      expect(priorCells[fresh.id]).toBeUndefined();
      // the free cell it took was not occupied before, in its own column
      const takenInItsColumn = before.nodes.filter(n => n.cell.col === fresh.cell.col).map(n => n.cell.row);
      expect(takenInItsColumn.length).toBeGreaterThan(0);
      expect(takenInItsColumn).not.toContain(fresh.cell.row);
    });
  });

  describe('determinism (C7)', () => {
    it('produces identical output for identical input', () => {
      expect(assembleWorld(sources())).toEqual(assembleWorld(sources()));
    });
  });
});

// ── S4: the care loop as prototype material ─────────────────────────────────

const NOW = '2026-09-03T09:12:00Z';
const TODAY = '2026-09-03T18:00:00Z';
const OVERDUE = '2026-09-01T08:00:00Z';
const LATER = '2026-09-08T08:00:00Z';

function reminder(id: number, over: Partial<ReminderDto> = {}): ReminderDto {
  return {
    id, plantId: 1, plantNickname: 'Plant 1', careType: 'WATERING', frequencyDays: 7,
    nextDueAt: LATER, enabled: true, recurring: true, ...over,
  };
}
function step(id: number, stepOrder: number, over: Partial<ReminderDto> = {}): ReminderDto {
  return reminder(id, {
    careType: 'PEST', frequencyDays: 0, recurring: false, treatmentPlanId: 201,
    treatmentPlanTitle: 'Root rot', stepOrder, instruction: `Step ${stepOrder}`, ...over,
  });
}
function plan(steps: ReminderDto[], over: Partial<TreatmentPlanDto> = {}): TreatmentPlanDto {
  return { id: 201, plantId: 1, title: 'Root rot', status: 'ACTIVE', createdAt: NOW, steps, ...over };
}
function treatment(over: Partial<TreatmentDto> = {}): TreatmentDto {
  return {
    id: 301, plantId: 1, plantNickname: 'Plant 1', diseaseName: 'Root rot', status: 'IN_PROGRESS',
    descriptionStatus: 'READY', diseaseDescription: 'A soil-borne rot.',
    diseaseDescriptionModel: 'ANTHROPIC_CLAUDE', treatmentPlanId: 201, createdAt: NOW,
    startedAt: OVERDUE, ...over,
  };
}
function log(id: number, over: Partial<CareLogDto> = {}): CareLogDto {
  return { id, plantId: 1, plantNickname: 'Plant 1', careType: 'WATERING', performedAt: OVERDUE, ...over };
}

const bodyOf = (w: ReturnType<typeof assembleWorld>, id: string) =>
  w.nodes.find(n => n.id === id)!.body ?? '';
/** The visible words of a body — the voice laws bind copy, not class names. */
const wordsOf = (html: string) => html.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ');
/** The block a `[data-course]` list occupies, so its contents can be examined. */
function courseBlock(html: string): string {
  const i = html.indexOf('data-course');
  return i < 0 ? '' : html.slice(i, html.indexOf('</dl>', i));
}
function vitalsBlock(html: string): string {
  const i = html.indexOf('data-vitals');
  return i < 0 ? '' : html.slice(i, html.indexOf('</dl>', i));
}

describe('S4 — the care loop as prototype material', () => {
  const careWorld = (over: Partial<WorldSources> = {}) =>
    assembleWorld(sources({ now: NOW, plants: [], species: [], identifications: [], ...over }));

  describe('the reminders hub', () => {
    it('lists routine reminders most overdue first and links drawn plants', () => {
      const w = careWorld({
        plants: [plant(1)],
        reminders: [
          reminder(1, { nextDueAt: TODAY, plantId: 2, plantNickname: 'Plant 2' }),
          reminder(2, { nextDueAt: OVERDUE, careType: 'PRUNING' }),
        ],
      });
      const b = bodyOf(w, 'n-reminders');
      expect(b.indexOf('Prune')).toBeLessThan(b.indexOf('Water ·'));
      expect(b).toContain('Overdue 2 days');
      expect(b).toMatch(/Today, \d\d:\d\d/);
      expect(b).toContain('data-goto="n-plant-1"');
      expect(w.nodes.find(n => n.id === 'n-reminders')!.recap).toBe('1 due today');
    });

    it('keeps reminders as rows — never nodes — and caps the list at six', () => {
      const many = Array.from({ length: 9 }, (_, i) => reminder(i + 1, { nextDueAt: LATER }));
      const w = careWorld({ reminders: many });
      expect(w.nodes.filter(n => /^n-reminder-/.test(n.id))).toHaveLength(0);
      expect(bodyOf(w, 'n-reminders').match(/data-arg="reminder:/g) ?? []).toHaveLength(6);
    });

    it('keeps step reminders out of the hub unless the setting allows them', () => {
      const rs = [reminder(1), step(701, 1)];
      expect(bodyOf(careWorld({ reminders: rs }), 'n-reminders')).not.toContain('Pest check');
      const on = careWorld({
        reminders: rs,
        settings: { ...emptySources().settings, stepReminders: 'also-in-reminders' },
      });
      expect(bodyOf(on, 'n-reminders')).toContain('Pest check');
    });

    it('renders the verbatim empty plot when nothing is scheduled', () => {
      const w = careWorld();
      expect(w.nodes.find(n => n.id === 'n-reminders')!.state).toBe('empty');
      const b = bodyOf(w, 'n-reminders');
      expect(b).toContain('state--empty');
      expect(b).toContain('This is an empty plot with room in it, not a failure');
    });

    it('says a snoozed reminder is snoozed on this device', () => {
      const w = careWorld({
        reminders: [reminder(1, { nextDueAt: OVERDUE })],
        snoozed: { 1: '2026-09-04T09:00:00Z' },
      });
      const b = bodyOf(w, 'n-reminders');
      expect(b).toContain('Snoozed until tomorrow · on this device');
      expect(b).not.toContain('data-arg="reminder:1"');
      expect(w.nodes.find(n => n.id === 'n-reminders')!.recap).toBe('Nothing due today');
    });
  });

  describe('a course', () => {
    const running = (over: Partial<TreatmentDto> = {}, pausedIds: number[] = []) =>
      careWorld({
        plants: [plant(1)],
        treatments: [treatment(over)],
        plansById: {
          201: plan([
            step(701, 1, { enabled: false, completedAt: OVERDUE }),
            step(702, 2, { nextDueAt: TODAY }),
            step(703, 3, { nextDueAt: LATER }),
          ]),
        },
        paused: pausedIds,
      });

    it('marks done rows, marks at most one row due, and names the first open step', () => {
      const b = bodyOf(running(), 'n-treatment-301');
      const course = courseBlock(b);
      expect(course).toContain('data-step-id="701" data-done="true"');
      expect(course.match(/data-due="true"/g) ?? []).toHaveLength(1);
      expect(course).toContain('data-step-id="702" data-done="false" data-due="true"');
      expect(b).toContain('Mark step 2 as done');
      expect(b).toContain('data-arg="reminder:702"');
      expect(b).toContain('Three steps');
    });

    it('disables the step stake and says paused when the course is paused here', () => {
      const w = running({}, [201]);
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.recap).toBe('paused · 1 of 3 done');
      expect(n.body).toContain('aria-disabled="true"');
      expect(n.body).toContain('data-paused="true"');
      expect(n.body).not.toContain('Mark step 2 as done');
      expect(n.body).toContain('Resume this course');
    });

    it('offers a draft the way to craft its plan', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment({ status: 'DRAFT', treatmentPlanId: undefined })],
      });
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.recap).toBe('Draft · no plan yet');
      expect(n.body).toContain('Craft the treatment plan');
    });

    it('renders a rate-limited draft as a first-class state with the minutes', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment({ status: 'DRAFT', treatmentPlanId: undefined })],
        rateLimited: { 301: { retryAfterSeconds: 900, at: NOW } },
      });
      const b = bodyOf(w, 'n-treatment-301');
      expect(b).toContain('You have used today');
      expect(b).toContain('They come back in 15 minutes');
      expect(b).toContain('Add the steps by hand');
      expect(b).not.toContain('Craft the treatment plan');
    });

    it('offers a failed write-up a retry and a model-free way on', () => {
      const b = bodyOf(
        running({ descriptionStatus: 'FAILED', diseaseDescription: undefined }),
        'n-treatment-301',
      );
      expect(b).toContain('The write-up did not come back');
      expect(b).toContain('Write it up again');
      expect(b).toContain('data-goto="n-care"');
    });

    it('is a loading node while its description is still being written', () => {
      const w = running({ descriptionStatus: 'PENDING', diseaseDescription: undefined });
      expect(w.nodes.find(x => x.id === 'n-treatment-301')!.state).toBe('loading');
      expect(bodyOf(w, 'n-treatment-301')).toContain('Still describing this disease');
    });

    it('keeps a finished course readable and archived, every row done', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment({ status: 'COMPLETED', completedAt: OVERDUE })],
        plansById: {
          201: plan([step(701, 1, { enabled: false, completedAt: OVERDUE })], { status: 'COMPLETED' }),
        },
      });
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.state).toBe('archived');
      expect(n.recap).toBe('Root rot · finished');
      expect(courseBlock(n.body!)).not.toContain('data-done="false"');
      expect(n.body).toContain('as part of its story');
      expect(n.body).toContain('One step<');
      expect(n.body).not.toContain('One steps');
      expect(n.body).not.toContain('Mark step');
    });

    it('holds no exit inside its course — a step is a mutation', () => {
      const w = running();
      for (const n of w.nodes) {
        const course = courseBlock(n.body ?? '');
        expect(course).not.toContain('<a');
        expect(course).not.toContain('data-goto');
        expect(course).not.toContain('hop');
      }
    });

    it('recaps the treatments hub and collapses courses at four or more', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [
          treatment(),
          treatment({ id: 302, status: 'DRAFT', treatmentPlanId: undefined }),
          treatment({ id: 303, status: 'DRAFT', treatmentPlanId: undefined }),
          treatment({ id: 304, status: 'COMPLETED', treatmentPlanId: undefined }),
        ],
        plansById: { 201: plan([step(701, 1, { nextDueAt: TODAY })]) },
      });
      expect(w.nodes.find(n => n.id === 'n-treatments')!.recap).toBe(
        '1 running · 2 waiting for a plan',
      );
      expect(w.nodes.filter(n => /^n-treatment-\d+$/.test(n.id)).map(n => n.id)).toEqual([
        'n-treatment-301',
        'n-treatment-302',
      ]);
      expect(w.nodes.find(n => n.id === 'n-treatments-more')!.recap).toBe('+2 more');
    });

    it('disables the step stake when the only open step is not due yet', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment()],
        plansById: {
          201: plan([
            step(701, 1, { enabled: false, completedAt: OVERDUE }),
            step(703, 3, { nextDueAt: LATER }),
          ]),
        },
      });
      const b = bodyOf(w, 'n-treatment-301');
      expect(b).toContain('aria-disabled="true"');
      expect(b).toContain('data-reason="Nothing is due today."');
      expect(b).not.toContain('data-arg="reminder:703"');
      expect(b).not.toContain('Mark step 3 as done');
    });

    it('wears a failed treatment-plans fetch on the course the plan belongs to', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment()],
        plansById: {},
        failures: [{ family: 'treatment-plans', ref: 201, status: 503, at: NOW }],
      });
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.state).toBe('failed');
      expect(n.recap).toBe('Did not come back');
      expect(n.body).toContain('the treatment plan did not come back');
      expect(n.body).not.toContain('Every step is done');
    });

    it('never invents 0 of 0 when a course plan did not arrive', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment()],
        plansById: {},
      });
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.recap).toBe('Root rot · the plan did not come back');
      expect(n.body).toContain('The steps did not come back');
      expect(n.body).not.toContain('Every step is done');
      expect(n.body).toContain('data-reason="The plan did not come back."');
    });

    it('says a dismissed course was dismissed, not finished', () => {
      const w = careWorld({
        plants: [plant(1)],
        treatments: [treatment({ status: 'DISMISSED', completedAt: OVERDUE })],
        plansById: { 201: plan([step(701, 1, { enabled: false, completedAt: OVERDUE })]) },
      });
      const n = w.nodes.find(x => x.id === 'n-treatment-301')!;
      expect(n.state).toBe('archived');
      expect(n.recap).toBe('Root rot · dismissed');
      expect(n.body).toContain('Dismissed');
      expect(n.body).not.toContain('Finished');
    });
  });

  describe('the journal', () => {
    it('draws entries as nodes linked to their plants and collapses at four', () => {
      const w = careWorld({
        plants: [plant(1)],
        careLogsByPlant: {
          1: [log(901), log(902, { performedAt: TODAY }), log(903), log(904, { careType: 'PRUNING' })],
        },
      });
      expect(w.nodes.find(n => n.id === 'n-journal')!.recap).toBe('4 entries');
      expect(w.nodes.filter(n => /^n-log-/.test(n.id)).map(n => n.id)).toEqual([
        'n-log-902',
        'n-log-904',
      ]);
      expect(w.nodes.find(n => n.id === 'n-journal-more')!.recap).toBe('+2 more');
      expect(w.edges).toEqual(expect.arrayContaining([['n-log-902', 'n-plant-1']]));
    });

    it('says the journal is empty in its own words', () => {
      const w = careWorld();
      expect(w.nodes.find(n => n.id === 'n-journal')!.state).toBe('empty');
      expect(bodyOf(w, 'n-journal')).toContain('Nothing written yet · a good place to start');
    });
  });

  describe('a plant', () => {
    const world = () =>
      careWorld({
        plants: [plant(1, { location: 'Office' })],
        reminders: [reminder(1, { nextDueAt: OVERDUE })],
        careLogsByPlant: { 1: [log(901, { performedAt: OVERDUE })] },
        treatments: [treatment()],
        plansById: { 201: plan([step(701, 1, { nextDueAt: TODAY })]) },
      });

    it('reads its vitals — course and last watering included — and holds no control', () => {
      const vitals = vitalsBlock(bodyOf(world(), 'n-plant-1'));
      expect(vitals).toContain('Course');
      expect(vitals).toContain('data-goto="n-treatment-301"');
      expect(vitals).toContain('Watered');
      expect(vitals).toContain('2 days ago');
      expect(vitals).toContain('Overdue 2 days');
      expect(vitals).not.toContain('<button');
    });

    it('writes a missing answer as missing on an unscanned plant', () => {
      const w = careWorld({ plants: [plant(1, { healthStatus: undefined, nextWaterDays: null })] });
      const n = w.nodes.find(x => x.id === 'n-plant-1')!;
      expect(n.state).toBe('unknown');
      expect(vitalsBlock(n.body!)).toContain('tag--unknown');
      expect(n.body).toContain('Set a watering schedule');
    });
  });

  describe('the voice', () => {
    it('never says loading, please wait, or a bare ellipsis', () => {
      const w = careWorld({
        plants: [plant(1)],
        reminders: [reminder(1)],
        treatments: [treatment({ descriptionStatus: 'PENDING', diseaseDescription: undefined })],
        plansById: { 201: plan([step(701, 1)]) },
        careLogsByPlant: { 1: [log(901)] },
      });
      for (const n of w.nodes) {
        const words = wordsOf(n.body ?? '');
        expect(words).not.toMatch(/loading|please wait|Something went wrong/i);
        expect(words).not.toContain('…');
      }
    });

    it('gives each hub its own staleness sentence', () => {
      const w = careWorld({
        plants: [plant(1)],
        reminders: [reminder(1)],
        careLogsByPlant: { 1: [log(901)] },
      });
      const said = new Set<string>();
      for (const n of w.nodes) {
        const m = /class="staleness">.*?<\/span>\s*([^<]+)</.exec(n.body ?? '');
        if (m) said.add(m[1].trim());
      }
      expect(said.size).toBeGreaterThanOrEqual(4);
    });

    it('says care history is not fetched when the page size is zero', () => {
      const w = careWorld({
        plants: [plant(1)],
        careLogsByPlant: { 1: [log(901)] },
        settings: { ...emptySources().settings, careLogPageSize: 0 },
      });
      const n = w.nodes.find(x => x.id === 'n-care')!;
      expect(n.state).toBe('empty');
      expect(n.recap).toBe('Care history not fetched');
      expect(n.body).toContain('turn it on in Settings');
    });

    it('says snoozing is not kept when the setting is off, and offers it when local', () => {
      const overdue = { plants: [plant(1)], reminders: [reminder(1, { nextDueAt: OVERDUE })] };
      const off = careWorld({
        ...overdue,
        settings: { ...emptySources().settings, snooze: 'off' as const },
      });
      expect(bodyOf(off, 'n-reminders')).toContain('Snoozing is not something PlantPal keeps yet.');
      expect(bodyOf(off, 'n-reminders')).not.toContain('Snooze the overdue one');
      const on = careWorld(overdue);
      expect(bodyOf(on, 'n-reminders')).toContain('Snooze the overdue one');
      expect(bodyOf(on, 'n-reminders')).not.toContain('Snoozing is not something');
    });

    it('never prints an API id anywhere when the reader turns them off', () => {
      const w = careWorld({
        plants: [plant(1)],
        species: [{ id: 11, scientificName: 'Ficus lyrata', commonName: 'Fig' }],
        identifications: [
          { id: 21, status: 'COMPLETED', createdAt: NOW, plantId: 1, commonName: 'Fig',
            species: 'Ficus lyrata', healthStatus: 'HEALTHY' },
        ],
        reminders: [reminder(1)],
        careLogsByPlant: { 1: [log(901)] },
        treatments: [treatment()],
        plansById: { 201: plan([step(701, 1, { nextDueAt: TODAY })]) },
        settings: { ...emptySources().settings, showApiIds: false },
      });
      for (const n of w.nodes) expect(n.body ?? '').not.toContain('state__id');
    });

    it('escapes user text in nicknames, instructions and notes', () => {
      const evil = '<img src=x onerror=alert(1)>';
      const w = careWorld({
        plants: [plant(1, { nickname: evil })],
        reminders: [reminder(1, { plantNickname: evil })],
        careLogsByPlant: { 1: [log(901, { notes: evil, plantNickname: evil })] },
        treatments: [treatment({ diseaseName: evil })],
        plansById: { 201: plan([step(701, 1, { instruction: evil })]) },
      });
      for (const id of ['n-reminders', 'n-care', 'n-journal', 'n-treatment-301', 'n-plant-1']) {
        expect(bodyOf(w, id)).not.toContain('<img');
        expect(bodyOf(w, id)).toContain('&lt;img');
      }
    });
  });
});

describe('S6 — the day, the knocks and the account', () => {
  const world = (over: Partial<WorldSources> = {}) =>
    assembleWorld(sources({ now: NOW, plants: [], species: [], identifications: [], ...over }));

  const dashboard = (over: Partial<DashboardDto> = {}): DashboardDto => ({
    healthSummary: { healthy: 1, issues: 1, unknown: 0 },
    overdueReminders: [],
    todayReminders: [],
    healthTrends: [],
    recentScans: [],
    plantCount: 2,
    speciesCount: 1,
    ...over,
  });

  describe('Today — a count, not a feed', () => {
    it('takes its rows from the dashboard buckets under server-day', () => {
      const w = world({
        plants: [plant(1, { nickname: 'Office Fig' }), plant(2, { nickname: 'Studio Fig' })],
        reminders: [reminder(1, { nextDueAt: LATER })],
        dashboard: dashboard({
          overdueReminders: [{ ...reminder(601, { plantNickname: 'Office Fig' }), daysOverdue: 2 }],
          todayReminders: [
            { ...reminder(602, { plantId: 2, plantNickname: 'Studio Fig', nextDueAt: TODAY }) },
          ],
        }),
      });
      const today = w.nodes.find(n => n.id === 'n-today')!;
      expect(today.recap).toBe('1 due · 1 overdue');
      const b = bodyOf(w, 'n-today');
      expect(wordsOf(b)).toContain('Office Fig · 2 days overdue');
      expect(wordsOf(b)).toContain('Studio Fig · today');
      expect(b).toContain("from PlantPal's own day");
      expect(b).not.toContain('the dashboard did not come back');
      // every name is a door
      expect(b).toContain('data-goto="n-plant-1"');
    });

    it('counts here under a rolling 24-hour window, and says so', () => {
      const w = world({
        plants: [plant(1)],
        reminders: [reminder(601, { nextDueAt: OVERDUE })],
        dashboard: dashboard({ overdueReminders: [], todayReminders: [] }),
        settings: { ...emptySources().settings, dueWindow: 'rolling-24h' },
      });
      expect(w.nodes.find(n => n.id === 'n-today')!.recap).toBe('0 due · 1 overdue');
      expect(bodyOf(w, 'n-today')).toContain('your due window is a rolling 24 hours');
    });

    it('leaves treatment steps to their course and names the course itself', () => {
      const w = world({
        plants: [plant(1)],
        reminders: [step(702, 2, { nextDueAt: TODAY })],
        treatments: [treatment()],
        plansById: { 201: plan([step(701, 1, { enabled: false }), step(702, 2, { nextDueAt: TODAY })]) },
        dashboard: dashboard({
          todayReminders: [{ ...step(702, 2, { nextDueAt: TODAY }) }],
        }),
      });
      const b = bodyOf(w, 'n-today');
      expect(w.nodes.find(n => n.id === 'n-today')!.recap).toBe('Nothing due · nothing overdue');
      expect(wordsOf(b)).toMatch(/Check on Root rot · day \d+ of \d+/);
    });

    it('carries no stake at all (the prototype gives Today none)', () => {
      const w = world({ plants: [plant(1)], dashboard: dashboard() });
      expect(bodyOf(w, 'n-today')).not.toContain('class="stake"');
      expect(bodyOf(w, 'n-today')).not.toContain('stake--quiet');
    });

    it('falls back to rows counted here when the dashboard did not come back', () => {
      const w = world({
        plants: [plant(1)],
        reminders: [reminder(601, { nextDueAt: OVERDUE })],
        failures: [{ family: 'dashboard', status: 503, at: NOW, message: 'Service unavailable' }],
      });
      const today = w.nodes.find(n => n.id === 'n-today')!;
      expect(today.state).toBe('failed');
      const b = bodyOf(w, 'n-today');
      expect(b).toContain('state--error');
      expect(b).toContain('Count again');
      // the count survives its own outage
      expect(wordsOf(b)).toContain('the dashboard did not come back');
      expect(wordsOf(b)).toContain('2 days overdue');
    });

    it('prints a real zero on a day-zero garden', () => {
      const w = world({});
      const today = w.nodes.find(n => n.id === 'n-today')!;
      expect(today.state).toBe('empty');
      expect(today.recap).toBe('Nothing due · nothing overdue');
      expect(bodyOf(w, 'n-today')).toContain('Nothing to do today');
      expect(bodyOf(w, 'n-today')).toContain('No plants yet');
    });
  });

  describe('the notifications panel', () => {
    const notifWorld = (over: Partial<WorldSources> = {}) =>
      world({
        plants: [plant(1)],
        reminders: [reminder(601, { nextDueAt: OVERDUE }), reminder(602, { nextDueAt: TODAY })],
        ...over,
      });

    it('reads the push state of this device', () => {
      expect(bodyOf(notifWorld(), 'n-reminders')).toContain('Off · enable in Settings · Notifications');
      expect(bodyOf(notifWorld({ push: 'on' }), 'n-reminders')).toContain(
        'On · this device · not during quiet hours',
      );
      expect(bodyOf(notifWorld({ push: 'unconfigured' }), 'n-reminders')).toContain(
        'Not configured on this server',
      );
      expect(bodyOf(notifWorld({ push: 'blocked' }), 'n-reminders')).toContain(
        'Blocked by the browser',
      );
    });

    it('drops the quiet-hours clause when quiet hours are off', () => {
      const b = bodyOf(
        notifWorld({ push: 'on', settings: { ...emptySources().settings, quietHours: 'off' } }),
        'n-reminders',
      );
      expect(b).toContain('On · this device');
      expect(b).not.toContain('not during quiet hours');
    });

    it('counts unread against the seen mark and leaves snoozed rows out', () => {
      const rows = (w: ReturnType<typeof assembleWorld>) => {
        const b = bodyOf(w, 'n-reminders');
        const i = b.indexOf('data-notifications');
        return b.slice(i, b.indexOf('</dl>', i));
      };
      expect(rows(notifWorld())).toContain('<dt>Unread</dt><dd>2</dd>');
      // seen after the overdue one became due: only the later one is unread
      const seen = notifWorld({
        settings: { ...emptySources().settings, seenAt: '2026-09-02T00:00:00Z' },
      });
      expect(rows(seen)).toContain('<dt>Unread</dt><dd>1</dd>');
      const snoozed = notifWorld({ snoozed: { 601: '2026-09-04T09:00:00Z' } });
      expect(rows(snoozed)).toContain('<dt>Unread</dt><dd>1</dd>');
      // nothing to acknowledge is said in the markup, not by hiding the stake
      const quiet = world({ plants: [plant(1)], reminders: [reminder(601, { nextDueAt: LATER })] });
      expect(bodyOf(quiet, 'n-reminders')).toContain('aria-disabled="true"');
    });

    it('never offers an email digest PlantPal does not send', () => {
      expect(bodyOf(notifWorld(), 'n-reminders')).toContain('Not offered by PlantPal');
    });
  });

  describe('the account', () => {
    const user = { firstName: 'Sam', lastName: 'Okafor', email: 'sam@example.org' };
    const prefs = {
      aiModelPreference: 'GITHUB_GPT4O',
      visionModelPreference: 'GITHUB_GPT4O',
      reasoningModelPreference: 'DEEPSEEK_R1',
      plantnetProject: 'all',
      plantnetLang: 'en',
      businessTier: false,
    } as WorldSources['preferences'];

    it('shows the server preferences in the gardener\'s words', () => {
      const b = bodyOf(world({ user, preferences: prefs }), 'n-account');
      expect(wordsOf(b)).toContain('Vision model GPT-4o');
      expect(wordsOf(b)).toContain('Reasoning model DeepSeek-R1');
      expect(wordsOf(b)).toContain('PlantNet all · en');
      expect(wordsOf(b)).toContain('Garden type Home garden');
      expect(b).toContain('sam@example.org');
    });

    it('says a users family that did not come back rather than inventing a value', () => {
      const b = bodyOf(world({ user, preferences: null }), 'n-account');
      expect(wordsOf(b)).toContain('Vision model Not fetched — try again');
      expect(wordsOf(b)).toContain('Garden type Not fetched — try again');
    });

    it('prints the device-local profile fields and the session window', () => {
      const b = bodyOf(
        world({
          user,
          preferences: prefs,
          sessionTimes: { issuedAt: '2026-09-03T08:41:00Z', expiresAt: '2026-09-03T18:40:00Z' },
          settings: { ...emptySources().settings, displayName: 'Sammy', units: 'imperial' },
        }),
        'n-account',
      );
      expect(wordsOf(b)).toContain('Display name Sammy');
      expect(wordsOf(b)).toContain('Imperial · °F');
      expect(wordsOf(b)).toContain('Quiet hours 21:00 – 07:30');
      expect(b).toContain('Sign out here');
      expect(b).toContain('Export everything');
      expect(wordsOf(b)).toMatch(/This session Since \d\d:\d\d · this device/);
    });

    it('is honest that a mock session is a mock session', () => {
      const b = bodyOf(world({ user, sessionTimes: { mock: true } }), 'n-account');
      expect(wordsOf(b)).toContain('This session mock session');
      expect(b).not.toContain('valid until');
    });
  });
});
