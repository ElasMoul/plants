import { CLEARANCE } from './geometry';
import { Adjacency } from './graph';
import { Point, Size } from './types';

/** A node as the clearance pass sees it: where it rests, and how big it is. */
export interface PlacementNode {
  anchor: Point;
  size: Size;
}

export interface ClearanceInput {
  focusId: string;
  /** One fixed id order. Every loop walks it, so nothing depends on iteration order. */
  order: string[];
  nodes: Record<string, PlacementNode>;
  adjacency: Adjacency;
  /** Arrange mode: the user places cards on the lattice itself — anchors only, no ring. */
  dragMode?: boolean;
  clearance?: number;
}

/** Target position per node id. */
export type TargetMap = Record<string, Point>;

/**
 * THE CLEARANCE PASS (theme-a round 9, fix 2/7). Given a focus, produce a target
 * position for every node:
 *
 *  - focus:      pinned at its own anchor. It never yields; the camera comes to it.
 *  - neighbours: placed on an ellipse around the focus whose radii are the two
 *                cards' real half-sizes + clearance, so a neighbour cannot overlap
 *                the focus. Each keeps the ANGLE its lattice home had from the
 *                focus (it arrives on the side you expected); angles then spread
 *                until no two neighbours' angular widths intersect.
 *  - everyone:   left at its anchor, then a fixed-iteration separation pass pushes
 *    else        apart any still-overlapping pair along its axis of least
 *                penetration. The focus has weight 0 and absorbs none of it.
 *
 * Pure and deterministic: fixed iteration counts, fixed order, no time, no
 * randomness — same input, same targets (C7). A non-neighbour may be pushed off
 * screen; it is still on the world view and still exactly where this rule puts it.
 */
export function computeTargets(input: ClearanceInput): TargetMap {
  const { focusId, order, nodes, adjacency } = input;
  const CLEAR = input.clearance ?? CLEARANCE;

  const t: TargetMap = {};
  for (const id of order) {
    t[id] = { x: nodes[id].anchor.x, y: nodes[id].anchor.y };
  }
  if (input.dragMode) return t;

  const F = nodes[focusId];
  const fx = F.anchor.x;
  const fy = F.anchor.y;
  t[focusId] = { x: fx, y: fy };

  const indexOf = (id: string) => order.indexOf(id);

  // --- the ring: direct neighbours placed on a clearance ellipse ---
  interface RingSlot {
    id: string;
    a: number; // angle around the focus
    rx: number;
    ry: number;
    half: number; // half the angular width this card subtends
  }
  const ring: RingSlot[] = [...(adjacency[focusId] ?? [])]
    .sort((a, b) => indexOf(a) - indexOf(b))
    .map((id, i) => {
      const n = nodes[id];
      let ax = n.anchor.x - fx;
      let ay = n.anchor.y - fy;
      if (!ax && !ay) {
        ax = Math.cos(i);
        ay = Math.sin(i);
      }
      return {
        id,
        a: Math.atan2(ay, ax),
        rx: F.size.w / 2 + n.size.w / 2 + CLEAR,
        ry: F.size.h / 2 + n.size.h / 2 + CLEAR,
        half: 0,
      };
    });
  for (const c of ring) {
    c.half = Math.atan2(nodes[c.id].size.w / 2 + CLEAR / 2, Math.max(60, c.rx));
  }
  ring.sort((a, b) => a.a - b.a || indexOf(a.id) - indexOf(b.id));

  // spread until neighbouring angular slots stop intersecting
  const TAU = Math.PI * 2;
  const m = ring.length;
  if (m > 1) {
    for (let pass = 0; pass < 60; pass++) {
      let moved = false;
      for (let i = 0; i < m; i++) {
        const A = ring[i];
        const B = ring[(i + 1) % m];
        let gap = B.a - A.a;
        while (gap < 0) gap += TAU;
        const need = A.half + B.half;
        if (gap < need) {
          const push = (need - gap) / 2;
          A.a -= push;
          B.a += push;
          moved = true;
        }
      }
      if (!moved) break;
    }
  }
  for (const c of ring) {
    t[c.id] = { x: fx + Math.cos(c.a) * c.rx, y: fy + Math.sin(c.a) * c.ry };
  }

  // --- separation pass: deterministic order, fixed iteration count ---
  const weight: Record<string, number> = {};
  for (const id of order) weight[id] = id === focusId ? 0 : 1;
  for (let it = 0; it < 160; it++) {
    let moved = false;
    for (let i = 0; i < order.length; i++) {
      for (let j = i + 1; j < order.length; j++) {
        const ia = order[i];
        const ib = order[j];
        const A = t[ia];
        const B = t[ib];
        const ra = nodes[ia];
        const rb = nodes[ib];
        const needX = (ra.size.w + rb.size.w) / 2 + CLEAR;
        const needY = (ra.size.h + rb.size.h) / 2 + CLEAR;
        const ox = needX - Math.abs(A.x - B.x);
        const oy = needY - Math.abs(A.y - B.y);
        if (ox <= 0 || oy <= 0) continue;
        const wsum = weight[ia] + weight[ib];
        if (!wsum) continue;
        moved = true;
        if (ox < oy) {
          const s = (A.x <= B.x ? -1 : 1) * ox;
          A.x += s * (weight[ia] / wsum);
          B.x -= s * (weight[ib] / wsum);
        } else {
          const s = (A.y <= B.y ? -1 : 1) * oy;
          A.y += s * (weight[ia] / wsum);
          B.y -= s * (weight[ib] / wsum);
        }
      }
    }
    if (!moved) break;
  }
  return t;
}

/** True when boxes a and b (centre + size) overlap, allowing `gap` px of slack. */
export function boxesOverlap(a: PlacementNode, at: Point, b: PlacementNode, bt: Point, gap = 0): boolean {
  const needX = (a.size.w + b.size.w) / 2 + gap;
  const needY = (a.size.h + b.size.h) / 2 + gap;
  return Math.abs(at.x - bt.x) < needX && Math.abs(at.y - bt.y) < needY;
}
