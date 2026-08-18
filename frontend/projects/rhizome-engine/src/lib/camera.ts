import { NAV_MS } from './geometry';
import { Point } from './types';

/** The camera over the one plane: a translate + uniform scale. */
export interface Camera {
  x: number;
  y: number;
  k: number;
}

/**
 * The camera that puts world `point` at screen `centre` at scale `k`.
 * cam = centre − point·k (theme-a camForPoint, with freeCentre passed in — the
 * free box is a DOM/viewport concern the Angular layer supplies).
 */
export function cameraForPoint(point: Point, k: number, centre: Point): Camera {
  return { x: centre.x - point.x * k, y: centre.y - point.y * k, k };
}

/** Where world `point` lands on screen under `cam`. Inverse of cameraForPoint. */
export function projize(point: Point, cam: Camera): Point {
  return { x: cam.x + point.x * cam.k, y: cam.y + point.y * cam.k };
}

/** The one easing for every hop: ease-out cubic, 1 − (1−p)³. */
export function easeOutCubic(p: number): number {
  const c = clamp01(p);
  return 1 - Math.pow(1 - c, 3);
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

export function lerpPoint(a: Point, b: Point, t: number): Point {
  return { x: lerp(a.x, b.x, t), y: lerp(a.y, b.y, t) };
}

function clamp01(v: number): number {
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** A polyline route with cumulative arc lengths, for arc-length sampling. */
export interface Route {
  points: Point[];
  /** length of each segment i (points[i] → points[i+1]) */
  seg: number[];
  /** cumulative length at each vertex; cum[0] = 0 */
  cum: number[];
  total: number;
}

/**
 * Build a route from a chain of points (the real vein polyline between focus and
 * destination). This is what makes the motion state which veins were crossed and
 * in which order (C10/C11): the camera travels ALONG these points, not straight
 * across the space.
 */
export function buildRoute(points: Point[]): Route {
  const seg: number[] = [];
  const cum: number[] = [0];
  for (let i = 1; i < points.length; i++) {
    const dx = points[i].x - points[i - 1].x;
    const dy = points[i].y - points[i - 1].y;
    const l = Math.hypot(dx, dy) || 1;
    seg.push(l);
    cum.push(cum[i - 1] + l);
  }
  return { points, seg, cum, total: cum[cum.length - 1] ?? 0 };
}

/**
 * Sample the point at fractional arc length `frac` ∈ [0,1] along the route. The
 * returned point ALWAYS lies on the route polyline (C21): the camera centre can
 * be sampled every frame and every sample is on the route. `frac` is typically
 * the eased progress `easeOutCubic(p)`.
 */
export function sampleRoute(route: Route, frac: number): Point {
  const { points, seg, cum, total } = route;
  if (points.length === 0) throw new Error('sampleRoute: empty route');
  if (points.length === 1 || total === 0) return { ...points[0] };
  const want = clamp01(frac) * total;
  let i = 1;
  while (i < cum.length - 1 && cum[i] < want) i++;
  const f = clamp01((want - cum[i - 1]) / (seg[i - 1] || 1));
  return lerpPoint(points[i - 1], points[i], f);
}

/**
 * The full camera at progress `p` ∈ [0,1] of one hop: the centre travels along
 * the route by (eased) arc length while the scale eases from k0 to k1. One code
 * path, one timing (NAV_MS) — every hop from every trigger uses this (C10/C11).
 */
export function travelCamera(
  route: Route,
  screenCentre: Point,
  k0: number,
  k1: number,
  p: number,
): Camera {
  const e = easeOutCubic(p);
  const at = sampleRoute(route, e);
  const k = lerp(k0, k1, e);
  return cameraForPoint(at, k, screenCentre);
}

/** The single hop duration (ms). Re-exported for the render layer's clock. */
export const HOP_DURATION_MS = NAV_MS;
