/**
 * The particle ("mote") field and the card drift — the two decorative motions.
 * Both are DETERMINISTIC: a fixed seed so the field never flickers into a
 * different arrangement on reload, and pure sines for the drift. Decoration is
 * permitted (Principle "what these do NOT forbid") but must be deterministic and
 * must never touch layout — drift is a render offset only, so two loads still
 * diff identical (C7/C9). Painting to a canvas is the Angular layer's job; the
 * numbers live here.
 */

const TAU = Math.PI * 2;

/** The theme-a fixed field seed. */
export const DEFAULT_FIELD_SEED = 20260730;

/**
 * The theme-a LCG. Deterministic pseudo-randomness in [0,1): same seed → same
 * sequence, so the field is stable across reloads.
 */
export function createRng(seed: number): () => number {
  let s = seed & 0x7fffffff;
  return () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    return s / 0x7fffffff;
  };
}

export interface Mote {
  x: number;
  y: number;
  vx: number;
  vy: number;
  r: number;
}

/** The mote count for a field of the given size (theme-a: min(190, w·h/11000)). */
export function moteCount(width: number, height: number): number {
  return Math.round(Math.min(190, (width * height) / 11000));
}

/**
 * Seed a field of motes for the given size. Deterministic in (width, height,
 * seed): identical inputs give byte-identical motes.
 */
export function seedField(width: number, height: number, seed: number = DEFAULT_FIELD_SEED): Mote[] {
  const n = moteCount(width, height);
  const rnd = createRng(seed);
  const motes: Mote[] = [];
  for (let i = 0; i < n; i++) {
    motes.push({
      x: rnd() * width,
      y: rnd() * height,
      vx: (rnd() - 0.5) * 0.14,
      vy: (rnd() - 0.5) * 0.14,
      r: 0.7 + rnd() * 1.5,
    });
  }
  return motes;
}

/**
 * Advance every mote by its velocity, wrapping at the field edges. Mutates and
 * returns the array (the field is a long-lived, per-frame-updated buffer).
 */
export function advanceField(motes: Mote[], width: number, height: number): Mote[] {
  for (const m of motes) {
    m.x += m.vx;
    m.y += m.vy;
    if (m.x < 0) m.x += width;
    if (m.x > width) m.x -= width;
    if (m.y < 0) m.y += height;
    if (m.y > height) m.y -= height;
  }
  return motes;
}

/** Link opacity between two motes distance² apart (0 beyond the 15000 cutoff). */
export function linkAlpha(distanceSquared: number): number {
  return distanceSquared < 15000 ? 1 - distanceSquared / 15000 : 0;
}

/** A per-node drift phase from its index — index, not chance (theme-a ph). */
export function driftPhase(index: number): number {
  return (index * 1.7113) % TAU;
}

export interface DriftOffset {
  jx: number;
  jy: number;
}

/** The maximum absolute drift on each axis (px). ~3–4px — "almost invisible". */
export const DRIFT_AMPLITUDE = 2.4;

/**
 * The 2–3px card drift at time `timeMs` for a node with the given phase. A pure
 * blend of slow sines — the same family the mote field is made of. A render
 * offset only; the focus never drifts (the caller passes drift only for
 * non-focus, non-still cards).
 */
export function cardDrift(phase: number, timeMs: number): DriftOffset {
  const a = DRIFT_AMPLITUDE;
  const jx = Math.sin(timeMs / 4300 + phase) * a + Math.sin(timeMs / 7100 + phase * 1.7) * a * 0.45;
  const jy = Math.cos(timeMs / 5200 + phase * 1.3) * a + Math.sin(timeMs / 8300 + phase) * a * 0.4;
  return { jx, jy };
}
