import { TestBed } from '@angular/core/testing';
import { anchorPosition } from '@plantpal/rhizome-engine';
import { WorldStore } from './world.store';

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
