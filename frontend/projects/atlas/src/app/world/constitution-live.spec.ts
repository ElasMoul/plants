/**
 * The constitution gate over the ASSEMBLED world (I7).
 *
 * constitution.spec.ts pins the laws against FIXTURE_WORLD — a hand-written board.
 * This suite pins them against the board the atlas actually draws in round 2/3: the
 * mock garden's sources run through `assembleWorld`, plus the real store, the real
 * actions service and the real mock backend behind HTTP. Every failure message
 * names the law it broke.
 */
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  anchorPosition,
  buildAdjacency,
  buildRoute,
  computeTargets,
  Point,
  rank,
  rankNameFor,
  shortestPath,
  Size,
  TargetMap,
  travelCamera,
} from '@plantpal/rhizome-engine';
import { provideSharedCore } from '@plantpal/shared-core';
import { MOCK_MODE, type MockMode } from '../core/mock-mode';
import { mockApiInterceptor } from '../mock/mock-api.interceptor';
import { buildMockSeed, seedToSources } from '../mock/mock.dataset';
import { actionsFor } from '../chrome/actions-for';
import { SettingsStore } from '../settings/settings.store';
import { assembleWorld } from './world.assembly';
import { WorldActionsService } from './world-actions.service';
import type { WorldData, WorldNode } from './world.model';
import { WorldStore } from './world.store';

// Node globals — declared locally because the atlas tsconfig excludes @types/node.
declare const require: (m: string) => { readFileSync: (p: string, e: string) => string; join: (...p: string[]) => string };
declare const __dirname: string;

const FIXED_NOW = Date.parse('2026-09-04T09:00:00.000Z');
const FIXED_NOW_ISO = new Date(FIXED_NOW).toISOString();

function gardenSources() {
  return seedToSources(buildMockSeed('garden', FIXED_NOW), FIXED_NOW_ISO);
}

const SOURCES = gardenSources();
const WORLD: WorldData = assembleWorld(SOURCES);
const DAY_ZERO: WorldData = assembleWorld(
  seedToSources(buildMockSeed('day-zero', FIXED_NOW), FIXED_NOW_ISO),
);

const RANK_SIZE: Record<string, Size> = {
  focus: { w: 436, h: 300 },
  near: { w: 300, h: 190 },
  far: { w: 240, h: 150 },
  fringe: { w: 180, h: 110 },
};

/** The full geography (clearance targets) of a world at a given focus. */
function geography(world: WorldData, focusId: string): TargetMap {
  const order = world.nodes.map(n => n.id);
  const adjacency = buildAdjacency(world.edges, order);
  const ranks = rank(focusId, adjacency);
  const nodes: Record<string, { anchor: Point; size: Size }> = {};
  for (const n of world.nodes) {
    nodes[n.id] = {
      anchor: anchorPosition({ cell: n.cell, offset: n.offset }),
      size: RANK_SIZE[rankNameFor(n.id, ranks)],
    };
  }
  return computeTargets({ focusId, order, nodes, adjacency });
}

function ids(world: WorldData): string[] {
  return world.nodes.map(n => n.id).sort();
}

function cellsOf(world: WorldData): Record<string, string> {
  return Object.fromEntries(world.nodes.map(n => [n.id, `${n.cell.col},${n.cell.row}`]));
}

/** Distance from a point to a polyline — the camera route, sampled. */
function distToPolyline(p: Point, pts: Point[]): number {
  let min = Infinity;
  for (let i = 1; i < pts.length; i++) {
    const a = pts[i - 1];
    const b = pts[i];
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const len2 = dx * dx + dy * dy;
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2));
    min = Math.min(min, Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy)));
  }
  return min;
}

function bodyOf(world: WorldData, id: string): string {
  return world.nodes.find(n => n.id === id)?.body ?? '';
}

/** Everything a reader can actually SEE — markup and attributes stripped. */
function allText(world: WorldData): string {
  return world.nodes
    .map(n =>
      [n.name, n.recap, n.recapNote ?? '', stripTags(n.body ?? ''), ...(n.detail ?? [])].join(' '),
    )
    .join('\n');
}

function stripTags(html: string): string {
  return html
    .replace(/<[^>]*>/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ');
}

/** Every `.stake` in a body, as [label, data-arg]. Bodies are HTML strings. */
function stakesIn(html: string): { label: string; arg?: string; at: number }[] {
  const out: { label: string; arg?: string; at: number }[] = [];
  const re = /<button[^>]*class="[^"]*\bstake\b[^"]*"[^>]*>([\s\S]*?)<\/button>/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    const tag = m[0].slice(0, m[0].indexOf('>'));
    const arg = /data-arg="([^"]*)"/.exec(tag)?.[1];
    const label = m[1].replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').trim();
    if (label) out.push({ label, arg, at: m.index });
  }
  return out;
}

function matchMediaReduced(): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: () => ({
      matches: true,
      addEventListener() {},
      removeEventListener() {},
      addListener() {},
      removeListener() {},
      dispatchEvent: () => false,
    }),
  });
}

describe('Rhizome constitution over the assembled world (I7)', () => {
  it('assembles a board worth testing', () => {
    expect(WORLD.nodes.length).toBeGreaterThan(15);
    expect(WORLD.nodes.some(n => /^n-treatment-\d+$/.test(n.id))).toBe(true);
    expect(WORLD.nodes.some(n => /^n-log-\d+$/.test(n.id))).toBe(true);
  });

  describe('C7 — geography is a formula', () => {
    it('assembles identical geography twice from the same sources', () => {
      const a = assembleWorld(gardenSources());
      const b = assembleWorld(gardenSources());
      expect(cellsOf(b)).toEqual(cellsOf(a));
      expect(geography(b, 'n-garden')).toEqual(geography(a, 'n-garden'));
      expect(geography(b, 'n-treatments')).toEqual(geography(a, 'n-treatments'));
    });
  });

  describe('C8 — a new node takes a free cell and moves nothing', () => {
    it('keeps every prior anchor when a plant is added', () => {
      const before = cellsOf(WORLD);
      const grown = assembleWorld({
        ...gardenSources(),
        plants: [
          ...SOURCES.plants,
          { id: 77, nickname: 'Newcomer', species: null, commonName: null },
        ],
        priorCells: Object.fromEntries(WORLD.nodes.map(n => [n.id, n.cell])),
      });
      const after = cellsOf(grown);
      for (const [id, cell] of Object.entries(before)) {
        expect({ law: 'C8 — a prior node kept its cell', id, cell: after[id] }).toEqual({
          law: 'C8 — a prior node kept its cell',
          id,
          cell,
        });
      }
    });
  });

  describe('C9, C22-C25 — degradation is material, never geometry', () => {
    it('draws identical targets whatever every node’s state is', () => {
      const base = geography(WORLD, 'n-garden');
      const states: NonNullable<WorldNode['state']>[] = [
        'loading',
        'failed',
        'empty',
        'unknown',
        'archived',
        'ready',
      ];
      for (const s of states) {
        const degraded: WorldData = { ...WORLD, nodes: WORLD.nodes.map(n => ({ ...n, state: s })) };
        expect(geography(degraded, 'n-garden')).toEqual(base);
      }
    });

    it('draws the outage scenario in the same cells as the garden it failed from', () => {
      const outage = assembleWorld(
        seedToSources(buildMockSeed('outage', FIXED_NOW), FIXED_NOW_ISO),
      );
      const garden = cellsOf(WORLD);
      for (const [id, cell] of Object.entries(cellsOf(outage))) {
        if (garden[id]) {
          expect({ law: 'C25 — geography never degrades', id, cell }).toEqual({
            law: 'C25 — geography never degrades',
            id,
            cell: garden[id],
          });
        }
      }
    });
  });

  describe('C4 — nothing unmounts', () => {
    it('keeps the node id set identical across ten hops', () => {
      matchMediaReduced();
      const store = TestBed.inject(WorldStore);
      store.setWorld(WORLD);
      const before = store.nodes().map(n => n.id).sort();
      const route = store.nodes().slice(0, 10).map(n => n.id);
      for (const id of route) store.go(id);
      expect(store.nodes().map(n => n.id).sort()).toEqual(before);
    });

    it('C21 — every sampled camera centre lies on the assembled vein polyline', () => {
      // the fixture board pins this too, but its edge set is not this one's: the
      // camera must travel the veins the ASSEMBLED world actually draws.
      const centre = { x: 640, y: 360 };
      const order = WORLD.nodes.map(n => n.id);
      const adjacency = buildAdjacency(WORLD.edges, order);
      const hops: Array<[string, string]> = [
        ['n-garden', 'n-reminders'],
        ['n-garden', 'n-today'],
        ['n-reminders', 'n-journal'],
      ];
      for (const [from, to] of hops) {
        const target = geography(WORLD, to);
        const chain = shortestPath(from, to, adjacency);
        expect({ hop: `${from}→${to}`, reachable: chain.length >= 2 }).toEqual({ hop: `${from}→${to}`, reachable: true });
        const routePts = chain.map(id => target[id]);
        const route = buildRoute(routePts);
        for (let p = 0; p <= 1.0001; p += 0.05) {
          const cam = travelCamera(route, centre, 0.6, 1, p);
          const at = { x: (centre.x - cam.x) / cam.k, y: (centre.y - cam.y) / cam.k };
          expect({ law: 'C21 — the camera travels the vein', hop: `${from}→${to}`, off: distToPolyline(at, routePts) < 1e-6 }).toEqual({
            law: 'C21 — the camera travels the vein',
            hop: `${from}→${to}`,
            off: true,
          });
        }
      }
    });

    it('keeps the id set and every cell across a reload of the same garden', () => {
      const again = assembleWorld({
        ...gardenSources(),
        priorCells: Object.fromEntries(WORLD.nodes.map(n => [n.id, n.cell])),
      });
      expect(ids(again)).toEqual(ids(WORLD));
      expect(cellsOf(again)).toEqual(cellsOf(WORLD));
    });

    it('keeps every hub and aggregate across a mutation reload', () => {
      const grown = assembleWorld({
        ...gardenSources(),
        careLogsByPlant: {
          ...SOURCES.careLogsByPlant,
          1: [
            { id: 999, plantId: 1, careType: 'WATERING', performedAt: FIXED_NOW_ISO },
            ...(SOURCES.careLogsByPlant[1] ?? []),
          ],
        },
        priorCells: Object.fromEntries(WORLD.nodes.map(n => [n.id, n.cell])),
      });
      const hubs = ids(WORLD).filter(id => !/^n-(plant|species|scan|treatment|log)-\d+$/.test(id));
      for (const id of hubs) expect(ids(grown)).toContain(id);
      const cells = cellsOf(grown);
      for (const id of hubs) expect({ id, cell: cells[id] }).toEqual({ id, cell: cellsOf(WORLD)[id] });
    });
  });

  describe('the density rule — 4 or more collapse to 2 plus one aggregate', () => {
    // [family, member id shape, aggregate id, how many the SEED holds] — the seed
    // count pins the rule from the other side: a family that stopped being
    // assembled at all would otherwise satisfy an upper bound of two.
    const families: [string, RegExp, string, number][] = [
      ['plants', /^n-plant-\d+$/, 'n-garden-more', SOURCES.plants.length],
      ['species', /^n-species-\d+$/, 'n-species-more', SOURCES.species.length],
      ['scans', /^n-scan-\d+$/, 'n-scans-more', SOURCES.identifications.length],
      [
        'journal',
        /^n-log-\d+$/,
        'n-journal-more',
        Object.values(SOURCES.careLogsByPlant).reduce((n, l) => n + l.length, 0),
      ],
      ['treatments', /^n-treatment-\d+$/, 'n-treatments-more', SOURCES.treatments.length],
    ];

    for (const [name, member, aggregate, seeded] of families) {
      it(`collapses ${name} to two plus one aggregate that has a vein`, () => {
        expect({ family: name, seeded: seeded >= 4 }).toEqual({ family: name, seeded: true });
        const drawn = WORLD.nodes.filter(n => member.test(n.id));
        expect(drawn.length).toBe(2);
        const more = WORLD.nodes.find(n => n.id === aggregate);
        expect(more).toBeDefined();
        expect(more?.recap ?? '').toMatch(/\+\d+ more/);
        expect(WORLD.edges.some(e => e[0] === aggregate || e[1] === aggregate)).toBe(true);
        expect(more?.unknown ?? false).toBe(false);
      });
    }
  });

  describe('C17 / C19 — steps and stakes live only in the full body', () => {
    it('places course rows and step stakes after the full-body boundary', () => {
      const course = WORLD.nodes.find(n => /^n-treatment-\d+$/.test(n.id) && /data-course/.test(n.body ?? ''));
      expect(course).toBeDefined();
      const html = course?.body ?? '';
      const full = html.indexOf('n__full');
      expect(full).toBeGreaterThan(-1);
      expect(html.indexOf('data-course')).toBeGreaterThan(full);
      // compared at the stake's OWN offset, never a re-search for its label text
      for (const s of stakesIn(html)) expect({ label: s.label, after: s.at > full }).toEqual({ label: s.label, after: true });
      // a non-focused card's one-clause recap carries no control
      expect(course?.recap ?? '').not.toContain('<button');
    });

    it('never puts an exit inside a course row (a step is a mutation)', () => {
      const courses = WORLD.nodes.filter(n => /data-course/.test(n.body ?? ''));
      expect(courses.length).toBeGreaterThan(0);
      for (const n of courses) {
        const dl = /<dl[^>]*data-course[^>]*>[\s\S]*?<\/dl>/.exec(n.body ?? '')?.[0] ?? '';
        const rows = dl.match(/<div class="row"[^>]*>[\s\S]*?<\/div>/g) ?? [];
        expect(rows.length).toBeGreaterThan(0);
        for (const row of rows) {
          expect({ law: 'C19 — a step is a mutation, never an exit', node: n.id, hasExit: /<a |data-goto|class="hop/.test(row) }).toEqual({
            law: 'C19 — a step is a mutation, never an exit',
            node: n.id,
            hasExit: false,
          });
        }
      }
    });

    it('never puts a control inside a vitals list (vitals are readouts)', () => {
      const vitals = WORLD.nodes
        .map(n => ({ id: n.id, dl: /<dl class="rows"[^>]*data-vitals[^>]*>[\s\S]*?<\/dl>/.exec(n.body ?? '')?.[0] }))
        .filter(v => v.dl);
      expect(vitals.length).toBeGreaterThan(0);
      for (const v of vitals) expect(/<button/.test(v.dl as string)).toBe(false);
    });
  });

  describe('C22 / C25 — the voice, and every failure in its own node', () => {
    it('never says loading, please wait, or something went wrong', () => {
      const text = allText(WORLD) + allText(DAY_ZERO);
      expect(text).not.toMatch(/\bloading\b/i);
      expect(text).not.toMatch(/please wait/i);
      expect(text).not.toMatch(/something went wrong/i);
      expect(text).not.toMatch(/spinner/i);
      // and no visible line promises a family that has already shipped
      expect(text).not.toMatch(/coming with the care loop/i);
      expect(text).not.toMatch(/care loop/i);
      // no bare ellipsis as a wait: no "…" that is not part of a quoted sentence
      expect(text.replace(/&hellip;/g, '…')).not.toMatch(/[a-z]…/);
    });

    it('gives every failed body a time and a way forward', () => {
      // the outage scenario answers 503 for reminders, the dashboard and one plan —
      // the shape the loader turns into per-family failures
      const outage = assembleWorld({
        ...gardenSources(),
        failures: [
          { family: 'reminders', status: 503, at: FIXED_NOW_ISO, message: 'Service unavailable' },
          { family: 'dashboard', status: 503, at: FIXED_NOW_ISO, message: 'Service unavailable' },
        ],
      });
      const family = outage.nodes.filter(n => n.state === 'failed' && n.failure);
      expect(family.map(n => n.id).sort()).toEqual(['n-reminders', 'n-today']);
      for (const n of family) {
        expect({ id: n.id, fact: n.failure?.fact }).toEqual({
          id: n.id,
          fact: expect.stringMatching(/did not come back \(503\)\./),
        });
        expect(n.failure?.time ?? '').not.toBe('');
        expect(n.failure?.dataNote ?? '').not.toBe('');
        expect((n.failure?.waysForward ?? []).length).toBeGreaterThanOrEqual(1);
        expect(n.failure?.fact ?? '').not.toMatch(/something went wrong/i);
        // the fact, the time and the retry are all inside the failing node's own body
        expect(n.body ?? '').toMatch(/state--error/);
        expect(stakesIn(n.body ?? '').map(s => s.label)).toContain('Fetch this region');
      }
      // the rest of the board is untouched — degradation is per node, never a banner
      expect(outage.nodes.filter(n => n.state === 'failed' && !n.failure).map(n => n.id)).toEqual([
        'n-scan-503',
      ]);
      expect(stakesIn(bodyOf(outage, 'n-scan-503')).map(s => s.label)).toContain(
        'Try the scan again',
      );
    });

    it('draws at least four different staleness sentences', () => {
      const lines = new Set<string>();
      for (const n of WORLD.nodes) {
        const m = /<div class="staleness">([\s\S]*?)<\/div>/.exec(n.body ?? '');
        if (m) lines.add(m[1].replace(/<[^>]*>/g, '').trim());
      }
      expect(lines.size).toBeGreaterThanOrEqual(4);
    });
  });

  describe('C26 — day zero is zeros with room in it, never a sample', () => {
    it('draws no record nodes and names no mock record', () => {
      expect(DAY_ZERO.nodes.filter(n => /^n-(plant|species|scan|treatment|log)-\d+$/.test(n.id))).toEqual([]);
      const text = allText(DAY_ZERO);
      for (const sample of ['Office Fig', 'Monstera', 'Terrace Lemon', 'Root rot', 'Spider mites']) {
        expect(text).not.toContain(sample);
      }
      // zero is printed as zero, and there is a way to begin
      expect(DAY_ZERO.nodes.find(n => n.id === 'n-garden')?.recap ?? '').toMatch(/^0 plants/);
      const empties = DAY_ZERO.nodes.filter(n => n.state === 'empty');
      expect(empties.length).toBeGreaterThan(0);
      expect(empties.some(n => stakesIn(n.body ?? '').length > 0)).toBe(true);
    });

    it('draws a deterministic board of hubs with room in it', () => {
      const again = assembleWorld(
        seedToSources(buildMockSeed('day-zero', FIXED_NOW), FIXED_NOW_ISO),
      );
      expect(cellsOf(again)).toEqual(cellsOf(DAY_ZERO));
      const zero = cellsOf(DAY_ZERO);
      for (const hub of ['n-garden', 'n-account', 'n-platform', 'n-ident', 'n-species', 'n-reminders', 'n-care', 'n-today', 'n-ask']) {
        expect({ law: 'C26 — every hub is still a place', hub, drawn: hub in zero }).toEqual({
          law: 'C26 — every hub is still a place',
          hub,
          drawn: true,
        });
      }
    });
  });

  describe('C15 / C16 — a mutation never moves the camera or the focus', () => {
    const mode: MockMode = { enabled: true, scenario: 'garden', latencyMs: 0 };
    let store: WorldStore;
    let actions: WorldActionsService;
    let settings: SettingsStore;

    beforeEach(() => {
      matchMediaReduced();
      localStorage.clear();
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(withInterceptors([mockApiInterceptor])),
          { provide: MOCK_MODE, useValue: mode },
          ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
        ],
      });
      store = TestBed.inject(WorldStore);
      actions = TestBed.inject(WorldActionsService);
      settings = TestBed.inject(SettingsStore);
      store.setWorld(WORLD);
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.runOnlyPendingTimers();
      jest.useRealTimers();
    });

    /** Every stake in every body, plus every rail action for every focus. */
    function everyPress(): { node: string; label: string; arg?: string }[] {
      const out: { node: string; label: string; arg?: string }[] = [];
      for (const n of WORLD.nodes) {
        for (const s of stakesIn(n.body ?? '')) out.push({ node: n.id, label: s.label, arg: s.arg });
        for (const label of actionsFor(n.id, WORLD.meta, settings.settings())) {
          out.push({ node: n.id, label });
        }
      }
      return out;
    }

    it('enumerates a real set of presses', () => {
      expect(everyPress().length).toBeGreaterThan(20);
    });

    it('leaves the focus, the camera and the node set untouched after every press', () => {
      const presses = everyPress();
      for (const p of presses) {
        store.setWorld(WORLD);
        const focus = store.focusId();
        const camera = JSON.stringify(store.camera());
        const set = store.nodes().map(n => n.id).sort();

        actions.dispatch(p.node, p.label, p.arg);
        // read before the timers run: say() clears its own announcement
        const said = store.announcement();
        jest.advanceTimersByTime(5000);

        expect({ law: 'C16 — a mutation never moves the focus', press: `${p.node} · ${p.label}`, focus: store.focusId() }).toEqual({
          law: 'C16 — a mutation never moves the focus',
          press: `${p.node} · ${p.label}`,
          focus,
        });
        expect({ law: 'C15 — a mutation never moves the camera', press: `${p.node} · ${p.label}`, camera: JSON.stringify(store.camera()) }).toEqual({
          law: 'C15 — a mutation never moves the camera',
          press: `${p.node} · ${p.label}`,
          camera,
        });
        expect(store.nodes().map(n => n.id).sort()).toEqual(set);
        // and the press was actually understood: a renamed label would fall to the
        // dispatcher's refusal branch and this sweep would otherwise stay green
        expect({ press: `${p.node} · ${p.label}`, refused: /is not something PlantPal can do/.test(said) }).toEqual({
          press: `${p.node} · ${p.label}`,
          refused: false,
        });
        actions.activeForm.set(null);
      }
    });

    it('has no camera or focus call anywhere in the actions service', () => {
      // the structural half of C15/C16: the file itself may not reach for the camera
      // (kept as a source assertion so a future contributor cannot quietly add one)
      const { readFileSync } = require('fs');
      const { join } = require('path');
      const src = (readFileSync(join(__dirname, 'world-actions.service.ts'), 'utf8') as string)
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
      expect(src).not.toMatch(/store\.go\(/);
      expect(src).not.toMatch(/frameFocus|camera\.set/);
    });
  });
});

describe('C2 — the companion, over the assembled world', () => {
  const TURNS = [1, 2, 3, 4].map(i => ({
    id: `t${i}`,
    askedAt: `2026-09-04T09:0${i}:00.000Z`,
    question: `question ${i}`,
    reply: `answer ${i}`,
    outcome: 'answered' as const,
  }));
  const THREAD = {
    key: 'garden',
    turns: TURNS,
    updatedAt: TURNS[TURNS.length - 1].askedAt,
  };
  const withThread = assembleWorld({ ...gardenSources(), chatThreads: [THREAD] });
  const ask = (world: WorldData): string =>
    world.nodes.find(n => n.id === 'n-ask')?.body ?? '';

  it('says its own staleness, and says when it is not sure', () => {
    const body = ask(withThread);
    expect(body).toMatch(/<div class="staleness">.*Answers use your garden as it stood at \d\d:\d\d/);
    expect(body).toContain('If it is not sure, it says so. It would rather be honest than confident.');
  });

  it('never says loading, never a bare ellipsis, never something went wrong', () => {
    const text = allText(withThread);
    expect(text).not.toMatch(/\bloading\b/i);
    expect(text).not.toMatch(/something went wrong/i);
    expect(text.replace(/&hellip;/g, '…')).not.toMatch(/[a-z]…/);
  });

  it('keeps its stakes in the focused body and never puts an exit in a feed row', () => {
    const body = ask(withThread);
    const full = body.indexOf('n__full');
    for (const s of stakesIn(body)) expect({ label: s.label, inFull: s.at > full }).toEqual({ label: s.label, inFull: true });
    const rows = body.match(/<div class="feed__row"[\s\S]*?<\/div>/g) ?? [];
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) expect(/<a |data-goto|class="hop/.test(row)).toBe(false);
    // the companion reads the garden: nothing in it is a mutation, and nothing an exit
    expect(stakesIn(body).map(s => s.label).sort()).toEqual(['Ask something', 'Read the whole thread']);
  });

  it('gives the companion an empty action rail — its stakes are body-only', () => {
    const settings = TestBed.inject(SettingsStore);
    expect(actionsFor('n-ask', WORLD.meta, settings.settings())).toEqual([]);
  });

  it('day zero prints a real zero and invents no plant', () => {
    const body = ask(DAY_ZERO);
    expect(body).toContain('Nothing asked yet.');
    expect(body).toContain('Ask about your garden');
    expect(body).not.toMatch(/Office Fig|Studio Fig/);
  });

  it('wears a chat failure as its own material — no overlay, no second dialog', () => {
    const failed = assembleWorld({
      ...gardenSources(),
      chatThreads: [THREAD],
      chatFailures: { garden: { kind: 'unavailable', retryAfterSeconds: null } },
    });
    const body = ask(failed);
    expect(body).toContain('The companion cannot reach its thinking right now');
    expect(body).not.toMatch(/ollama/i);
    expect(body).not.toMatch(/role="dialog"/);
    // and no other node on the board says anything about it
    for (const n of failed.nodes) {
      if (n.id === 'n-ask') continue;
      expect(n.body ?? '').not.toMatch(/cannot reach its thinking/);
    }
  });

  it('never reaches for fetch or EventSource anywhere in the chat code', () => {
    const { readFileSync } = require('fs');
    const { join } = require('path');
    for (const file of ['chat.client.ts', 'chat.store.ts', 'sse-parse.ts', 'ask-copy.ts']) {
      const src = (readFileSync(join(__dirname, file), 'utf8') as string)
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
      expect({ file, usesFetch: /\bfetch\s*\(/.test(src) }).toEqual({ file, usesFetch: false });
      expect({ file, usesEventSource: /EventSource/.test(src) }).toEqual({ file, usesEventSource: false });
    }
  });
});
