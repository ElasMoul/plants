/**
 * The constitution QA gate (F2). Encodes the pinned atlas invariants (C1-C25) as
 * executable assertions against the real fixture world + store + engine — the
 * automated form of design-system/layout-determinism.json, traversal-map.json and
 * atlas-degradation-spec.json. If a change breaks a Rhizome law, this fails.
 */
import { TestBed } from '@angular/core/testing';
import {
  anchorPosition,
  buildAdjacency,
  buildRoute,
  computeTargets,
  easeOutCubic,
  Point,
  rank,
  rankNameFor,
  shortestPath,
  Size,
  TargetMap,
  travelCamera,
} from '@plantpal/rhizome-engine';
import { FIXTURE_WORLD } from './world.fixture';
import { WorldData, WorldNode } from './world.model';
import { WorldStore } from './world.store';

const RANK_SIZE: Record<string, Size> = {
  focus: { w: 436, h: 300 },
  near: { w: 300, h: 190 },
  far: { w: 240, h: 150 },
  fringe: { w: 180, h: 110 },
};

/** Compute the full geography (clearance targets) for a world at a given focus. */
function geography(world: WorldData, focusId: string): TargetMap {
  const order = world.nodes.map(n => n.id);
  const adjacency = buildAdjacency(world.edges, order);
  const ranks = rank(focusId, adjacency);
  const nodes: Record<string, { anchor: Point; size: Size }> = {};
  for (const n of world.nodes) {
    nodes[n.id] = { anchor: anchorPosition({ cell: n.cell, offset: n.offset }), size: RANK_SIZE[rankNameFor(n.id, ranks)] };
  }
  return computeTargets({ focusId, order, nodes, adjacency });
}

function anchorsOf(world: WorldData): Record<string, Point> {
  return Object.fromEntries(world.nodes.map(n => [n.id, anchorPosition({ cell: n.cell, offset: n.offset })]));
}

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

describe('Rhizome constitution gate (F2)', () => {
  describe('C7 — geography is a formula (determinism)', () => {
    it('computes identical geography twice for the same focus', () => {
      expect(geography(FIXTURE_WORLD, 'n-fig')).toEqual(geography(FIXTURE_WORLD, 'n-fig'));
    });
  });

  describe('C8 — a new node moves nothing', () => {
    it('leaves every existing anchor unchanged when a node is inserted', () => {
      const before = anchorsOf(FIXTURE_WORLD);
      const withNew: WorldData = {
        ...FIXTURE_WORLD,
        nodes: [...FIXTURE_WORLD.nodes, { id: 'n-new', glyph: '♠', cell: { col: 12, row: 12 }, kind: 'plant', kindLabel: 'Plant', name: 'New', recap: 'new' }],
      };
      const after = anchorsOf(withNew);
      for (const id of Object.keys(before)) {
        expect(after[id]).toEqual(before[id]);
      }
    });
  });

  describe('C9 / C22-C25 — degradation never moves the geography', () => {
    it('produces identical geography whatever every node’s state is', () => {
      const base = geography(FIXTURE_WORLD, 'n-fig');
      const states: WorldNode['state'][] = ['loading', 'failed', 'empty', 'unknown', 'ready'];
      states.forEach((s, i) => {
        const degraded: WorldData = {
          ...FIXTURE_WORLD,
          nodes: FIXTURE_WORLD.nodes.map(n => ({ ...n, state: s })),
        };
        expect(geography(degraded, 'n-fig')).toEqual(base);
      });
    });
  });

  describe('C1 / C4 — one plane, nothing unmounts', () => {
    it('keeps the element set identical after ten hops', () => {
      Object.defineProperty(window, 'matchMedia', {
        writable: true, configurable: true,
        value: () => ({ matches: true, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, dispatchEvent: () => false }),
      });
      const store = TestBed.inject(WorldStore);
      const idsBefore = store.nodes().map(n => n.id).sort();
      const route = ['n-garden', 'n-office', 'n-garden', 'n-problems', 'n-underwater', 'n-treatment', 'n-care', 'n-ident', 'n-fig', 'n-species'];
      for (const id of route) store.go(id);
      const idsAfter = store.nodes().map(n => n.id).sort();
      expect(idsAfter).toEqual(idsBefore);
    });
  });

  describe('C12 — rank is graph distance from the focus', () => {
    it('names the focus, its neighbours and beyond correctly', () => {
      const adjacency = buildAdjacency(FIXTURE_WORLD.edges, FIXTURE_WORLD.nodes.map(n => n.id));
      const ranks = rank('n-fig', adjacency);
      expect(rankNameFor('n-fig', ranks)).toBe('focus');
      expect(rankNameFor('n-garden', ranks)).toBe('near'); // direct neighbour
      expect(rankNameFor('n-office', ranks)).toBe('far'); // garden's plant, 2 hops
    });
  });

  describe('C10 / C11 / C21 — the camera travels along the vein polyline', () => {
    it('every sampled camera centre lies on the route for representative hops', () => {
      const centre = { x: 640, y: 360 };
      const hops: Array<[string, string]> = [
        ['n-fig', 'n-treatment'],
        ['n-fig', 'n-unknown'],
        ['n-species', 'n-office'],
      ];
      for (const [from, to] of hops) {
        const target = geography(FIXTURE_WORLD, to);
        const adjacency = buildAdjacency(FIXTURE_WORLD.edges, FIXTURE_WORLD.nodes.map(n => n.id));
        const chain = shortestPath(from, to, adjacency);
        const routePts = chain.map(id => target[id]);
        const route = buildRoute(routePts);
        for (let p = 0; p <= 1.0001; p += 0.05) {
          const cam = travelCamera(route, centre, 0.6, 1, p);
          const at = { x: (centre.x - cam.x) / cam.k, y: (centre.y - cam.y) / cam.k };
          expect(distToPolyline(at, routePts)).toBeLessThan(1e-6);
        }
        expect(easeOutCubic(1)).toBe(1); // one easing, pinned
      }
    });
  });
});
