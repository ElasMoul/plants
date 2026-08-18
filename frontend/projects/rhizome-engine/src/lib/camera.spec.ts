import {
  buildRoute,
  cameraForPoint,
  easeOutCubic,
  HOP_DURATION_MS,
  lerpPoint,
  projize,
  Route,
  sampleRoute,
  travelCamera,
} from './camera';
import { Point } from './types';

/** Distance from point p to segment a→b; 0 means p lies on the segment. */
function distToSegment(p: Point, a: Point, b: Point): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  if (len2 === 0) return Math.hypot(p.x - a.x, p.y - a.y);
  let t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

/** Smallest distance from p to any segment of the polyline. */
function distToPolyline(p: Point, points: Point[]): number {
  let min = Infinity;
  for (let i = 1; i < points.length; i++) {
    min = Math.min(min, distToSegment(p, points[i - 1], points[i]));
  }
  return min;
}

describe('camera — arc-length travel (B4)', () => {
  describe('cameraForPoint', () => {
    it('puts the world point at the screen centre', () => {
      const centre: Point = { x: 640, y: 360 };
      const cam = cameraForPoint({ x: 800, y: 980 }, 1, centre);
      expect(projize({ x: 800, y: 980 }, cam)).toEqual(centre);
    });
    it('honours scale', () => {
      const centre: Point = { x: 100, y: 100 };
      const cam = cameraForPoint({ x: 50, y: 50 }, 2, centre);
      expect(cam.k).toBe(2);
      expect(projize({ x: 50, y: 50 }, cam)).toEqual(centre);
    });
  });

  describe('easeOutCubic', () => {
    it('is pinned at the endpoints', () => {
      expect(easeOutCubic(0)).toBe(0);
      expect(easeOutCubic(1)).toBe(1);
    });
    it('clamps out-of-range input', () => {
      expect(easeOutCubic(-1)).toBe(0);
      expect(easeOutCubic(2)).toBe(1);
    });
    it('is monotonic increasing', () => {
      let prev = -1;
      for (let p = 0; p <= 1.0001; p += 0.1) {
        const e = easeOutCubic(p);
        expect(e).toBeGreaterThanOrEqual(prev);
        prev = e;
      }
    });
  });

  describe('buildRoute', () => {
    it('accumulates segment lengths', () => {
      const r = buildRoute([{ x: 0, y: 0 }, { x: 3, y: 4 }, { x: 3, y: 9 }]);
      expect(r.seg).toEqual([5, 5]);
      expect(r.cum).toEqual([0, 5, 10]);
      expect(r.total).toBe(10);
    });
  });

  describe('sampleRoute', () => {
    const route = buildRoute([{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 100 }]);
    it('returns the first point at frac 0 and the last at frac 1', () => {
      expect(sampleRoute(route, 0)).toEqual({ x: 0, y: 0 });
      expect(sampleRoute(route, 1)).toEqual({ x: 100, y: 100 });
    });
    it('samples by arc length across the bend (frac 0.5 → the corner)', () => {
      // total length 200; halfway (100) is exactly the corner vertex.
      expect(sampleRoute(route, 0.5)).toEqual({ x: 100, y: 0 });
    });
    it('handles a degenerate single-point route', () => {
      expect(sampleRoute(buildRoute([{ x: 7, y: 7 }]), 0.5)).toEqual({ x: 7, y: 7 });
    });

    it('C21: every sample lies on the route polyline', () => {
      const bent: Route = buildRoute([
        { x: 0, y: 0 },
        { x: 200, y: 120 },
        { x: 200, y: 400 },
        { x: 500, y: 400 },
      ]);
      for (let p = 0; p <= 1.0001; p += 0.02) {
        const pt = sampleRoute(bent, easeOutCubic(p));
        expect(distToPolyline(pt, bent.points)).toBeLessThan(1e-6);
      }
    });
  });

  describe('travelCamera', () => {
    const route = buildRoute([{ x: 0, y: 0 }, { x: 300, y: 0 }, { x: 300, y: 300 }]);
    const centre: Point = { x: 640, y: 360 };

    it('at p=0 frames the route start at scale k0', () => {
      const cam = travelCamera(route, centre, 0.5, 1, 0);
      expect(cam.k).toBe(0.5);
      expect(projize(route.points[0], cam)).toEqual(centre);
    });
    it('at p=1 frames the route end at scale k1', () => {
      const cam = travelCamera(route, centre, 0.5, 1, 1);
      expect(cam.k).toBe(1);
      expect(projize(route.points[route.points.length - 1], cam)).toEqual(centre);
    });
    it('C21: the framed centre lies on the polyline at every p', () => {
      for (let p = 0; p <= 1.0001; p += 0.05) {
        const cam = travelCamera(route, centre, 0.6, 1, p);
        // the world point currently at screen centre:
        const at = { x: (centre.x - cam.x) / cam.k, y: (centre.y - cam.y) / cam.k };
        expect(distToPolyline(at, route.points)).toBeLessThan(1e-6);
      }
    });
  });

  describe('helpers', () => {
    it('lerpPoint interpolates', () => {
      expect(lerpPoint({ x: 0, y: 0 }, { x: 10, y: 20 }, 0.5)).toEqual({ x: 5, y: 10 });
    });
    it('exposes the single hop duration', () => {
      expect(HOP_DURATION_MS).toBe(300);
    });
  });
});
