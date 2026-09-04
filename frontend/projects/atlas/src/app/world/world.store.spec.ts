import { TestBed } from '@angular/core/testing';
import { anchorPosition } from '@plantpal/rhizome-engine';
import { LAYOUT_KEY, WorldStore } from './world.store';
import { MOCK_MODE } from '../core/mock-mode';
import { SETTINGS_KEY } from '../settings/settings.model';
import type { WorldData, WorldNode } from './world.model';

describe('WorldStore (C4 — engine ↔ Angular wiring)', () => {
  let store: WorldStore;

  beforeEach(() => {
    // jsdom has no matchMedia; report reduced motion so go() settles synchronously
    // (the animated path is exercised live in the browser + via B4 engine tests).
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: (query: string) => ({
        matches: true,
        media: query,
        onchange: null,
        addListener: () => undefined,
        removeListener: () => undefined,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }),
    });
    TestBed.configureTestingModule({});
    store = TestBed.inject(WorldStore);
  });

  it('starts focused on the fixture initial focus', () => {
    expect(store.focusId()).toBe('n-fig');
    expect(store.rankNameOf('n-fig')).toBe('focus');
  });

  it('ranks direct neighbours of the focus as near', () => {
    // n-garden is a direct neighbour of n-fig in the fixture.
    expect(store.rankNameOf('n-garden')).toBe('near');
  });

  it('pins the focus target at its lattice anchor (C7)', () => {
    // n-fig cell (2,5) → 200+2*300, 80+5*180 = (800, 980)
    expect(store.positionOf('n-fig')).toEqual(anchorPosition({ cell: { col: 2, row: 5 } }));
  });

  describe('go() — a hop recomputes rank around the new focus', () => {
    it('moves the focus and re-ranks', () => {
      store.go('n-garden');
      expect(store.focusId()).toBe('n-garden');
      expect(store.rankNameOf('n-garden')).toBe('focus');
      // the previous focus is now a direct neighbour → near
      expect(store.rankNameOf('n-fig')).toBe('near');
      // garden's own plants become near
      expect(store.rankNameOf('n-office')).toBe('near');
    });

    it('ignores a hop to the current focus or an unknown id', () => {
      store.go('n-fig');
      expect(store.focusId()).toBe('n-fig');
      store.go('does-not-exist');
      expect(store.focusId()).toBe('n-fig');
    });

    it('frames the new focus at the screen centre', () => {
      store.setScreenCentre({ x: 640, y: 360 });
      store.go('n-garden');
      const cam = store.camera();
      const at = store.positionOf('n-garden');
      // projecting the focus under the camera lands on the screen centre
      expect(cam.x + at.x * cam.k).toBeCloseTo(640, 6);
      expect(cam.y + at.y * cam.k).toBeCloseTo(360, 6);
    });
  });

  describe('travelAlongVein()', () => {
    it('from the focus goes to the vein’s other end', () => {
      store.travelAlongVein('n-fig', 'n-garden');
      expect(store.focusId()).toBe('n-garden');
    });

    it('from elsewhere heads to the nearer end first', () => {
      // focus is n-fig. Edge n-garden-more—n-unknown: garden-more (dist 2, via
      // n-garden) is nearer than unknown (dist 3), so we travel to garden-more.
      store.travelAlongVein('n-unknown', 'n-garden-more');
      expect(store.focusId()).toBe('n-garden-more');
    });
  });

  describe('the board as the loader reads it back', () => {
    it('cellsSnapshot returns every node\u2019s cell', () => {
      const snap = store.cellsSnapshot();
      const nodes = store.nodes();
      expect(Object.keys(snap)).toHaveLength(nodes.length);
      for (const n of nodes) expect(snap[n.id]).toEqual({ col: n.cell.col, row: n.cell.row });
    });

    it('distanceTo counts veins and answers minus one when unreachable', () => {
      expect(store.distanceTo('n-fig')).toBe(0);
      expect(store.distanceTo('n-garden')).toBe(1);
      expect(store.distanceTo('n-office')).toBe(2);
      expect(store.distanceTo('nowhere-at-all')).toBe(-1);
    });
  });

  describe('camera framing', () => {
    it('zoomBy keeps the focus framed and clamps scale', () => {
      store.setScreenCentre({ x: 500, y: 300 });
      store.frameFocus(1);
      store.zoomBy(0.8);
      const cam = store.camera();
      const at = store.positionOf('n-fig');
      expect(cam.k).toBeCloseTo(0.8, 6);
      expect(cam.x + at.x * cam.k).toBeCloseTo(500, 6);
    });
  });
});

describe('WorldStore (S7 — the store reads its settings)', () => {
  function node(id: string, col: number, row: number): WorldNode {
    return {
      id, cell: { col, row }, kind: 'guide', kindLabel: 'Guide', glyph: '◷',
      name: id, recap: 'two words',
    };
  }
  const world: WorldData = {
    nodes: [node('n-garden', 2, 2), node('n-today', 2, 3)],
    edges: [['n-garden', 'n-today']],
    initialFocus: 'n-garden',
  };

  function make(settings: Record<string, unknown> = {}, layout?: unknown): WorldStore {
    localStorage.clear();
    if (Object.keys(settings).length) localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    if (layout) localStorage.setItem(LAYOUT_KEY, JSON.stringify(layout));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    return TestBed.inject(WorldStore);
  }

  beforeEach(() => {
    // the system asks for reduced motion throughout this block
    Object.defineProperty(window, 'matchMedia', {
      writable: true, configurable: true,
      value: (query: string) => ({
        matches: true, media: query, onchange: null,
        addListener: () => undefined, removeListener: () => undefined,
        addEventListener: () => undefined, removeEventListener: () => undefined,
        dispatchEvent: () => false,
      }),
    });
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.useRealTimers();
    localStorage.clear();
  });

  it('clears the announcement after the configured delay, and never when it is zero', () => {
    const store = make();
    store.say('Watered Office Fig.');
    jest.advanceTimersByTime(1799);
    expect(store.announcement()).toBe('Watered Office Fig.');
    // the system asks for reduced motion, so the sentence is given 2600ms to be read
    jest.advanceTimersByTime(799);
    expect(store.announcement()).toBe('Watered Office Fig.');
    jest.advanceTimersByTime(2);
    expect(store.announcement()).toBe('');

    const keeps = make({ general: { announceMs: 0 } });
    keeps.say('Kept.');
    jest.advanceTimersByTime(60_000);
    expect(keeps.announcement()).toBe('Kept.');
  });

  it('ignores the system reduced-motion setting when told not to follow it', () => {
    const store = make({ appearance: { followSystemMotion: false } });
    store.say('Watered Office Fig.');
    // not following the system: the plain 1800ms applies, not the 2600ms reading time
    jest.advanceTimersByTime(1800);
    expect(store.announcement()).toBe('');
  });

  it('opens on Today when that is what the reader asked for and it exists', () => {
    expect(make().focusId()).not.toBe('n-today');
    const today = make({ general: { initialFocus: 'today' } });
    today.setWorld(world);
    expect(today.focusId()).toBe('n-today');

    const garden = make({ general: { initialFocus: 'today' } });
    garden.setWorld({ ...world, nodes: [node('n-garden', 2, 2)], edges: [] });
    expect(garden.focusId()).toBe('n-garden'); // nothing is faked when Today is not there
  });

  it('keeps the layout only while remembering is allowed, and seeds it back', () => {
    const store = make();
    store.setWorld(world);
    store.setOffset('n-today', { x: 12, y: -8 });
    store.setModeFor('n-today', 'min');
    jest.advanceTimersByTime(250);
    // the two gardens keep their geography apart — this is the live one's branch
    const blob = JSON.parse(localStorage.getItem(LAYOUT_KEY) as string);
    expect(blob.mock).toEqual({ cells: {}, offsets: {}, modes: {} });
    const kept = blob.live;
    expect(kept.offsets['n-today']).toEqual({ x: 12, y: -8 });
    expect(kept.modes['n-today']).toBe('min');
    expect(kept.cells['n-today']).toEqual({ col: 2, row: 3 });

    // a fresh session comes back to what it left
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const next = TestBed.inject(WorldStore);
    expect(next.offsets()['n-today']).toEqual({ x: 12, y: -8 });
    expect(next.modeOf('n-today')).toBe('min');

    const forgetful = make({ privacy: { rememberLayout: false } }, { offsets: { 'n-x': { x: 1, y: 1 } } });
    expect(forgetful.offsets()).toEqual({});
    forgetful.setOffset('n-garden', { x: 4, y: 4 });
    jest.advanceTimersByTime(250);
    expect(localStorage.getItem(LAYOUT_KEY)).toBeNull();
  });

  it('never applies a mock session’s geography to the real garden', () => {
    const live = make();
    live.setWorld(world);
    live.setOffset('n-today', { x: 12, y: -8 });
    jest.advanceTimersByTime(250);

    // the same page, switched to the mock garden: it starts from nothing
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: MOCK_MODE, useValue: { enabled: true, scenario: 'garden', latencyMs: 0 } }],
    });
    const mock = TestBed.inject(WorldStore);
    expect(mock.offsets()).toEqual({});
    mock.setWorld(world);
    mock.setOffset('n-today', { x: 400, y: 400 });
    jest.advanceTimersByTime(250);

    // and the live garden still has its own, untouched
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const back = TestBed.inject(WorldStore);
    expect(back.offsets()['n-today']).toEqual({ x: 12, y: -8 });
  });

  it('cellsSnapshot keeps a cell the board has not drawn this time', () => {
    const store = make({}, { cells: { 'n-gone': { col: 9, row: 9 } }, offsets: {}, modes: {} });
    store.setWorld(world);
    const snap = store.cellsSnapshot();
    expect(snap['n-gone']).toEqual({ col: 9, row: 9 });
    expect(snap['n-today']).toEqual({ col: 2, row: 3 });
  });

  it('holds the hubs of THIS board pending, or the pinned set when asked', () => {
    const hubs = make();
    hubs.setWorld(world);
    hubs.probeSlow.set(true);
    expect(hubs.isPending('n-today')).toBe(true);
    expect(hubs.isPending('n-species-more')).toBe(false);

    const pinned = make({ advanced: { slowNodes: 'fixture' } });
    pinned.probeSlow.set(true);
    expect(pinned.isPending('n-species-more')).toBe(true);
    expect(pinned.isPending('n-today')).toBe(false);
  });
});
