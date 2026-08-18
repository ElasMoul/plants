/**
 * Core value types for the Rhizome engine.
 *
 * The engine is framework-agnostic (no Angular, no DOM). It is the home of the
 * Rhizome constitution's computable invariants — determinism (C7-C9), rank as
 * graph distance (C3/C12), one camera timing (C10/C11/C13/C21). Every function
 * here is pure: same inputs → same outputs, no time, no randomness, no order
 * dependence.
 */

export interface Point {
  x: number;
  y: number;
}

/** A cell on the integer lattice. A node's cell is stable for the life of the record. */
export interface Cell {
  col: number;
  row: number;
}

/** A measured (or assumed) card size, in world px. */
export interface Size {
  w: number;
  h: number;
}

/**
 * The lattice: origin + cell × pitch. Pitch may change between interfaces (a
 * uniform re-space), but which cell a node occupies never does — so switching
 * interface rescales the whole geography while every node keeps its place
 * relative to every other (C7, C8).
 */
export interface LatticeConfig {
  originX: number;
  originY: number;
  pitchX: number;
  pitchY: number;
}

/**
 * A node as the engine sees it: identity, its lattice cell, the user's own
 * persisted drag offset (Arrange mode), and its settled size. Positions are NOT
 * stored on the node — they are computed from these by pure functions.
 */
export interface LatticeNode {
  id: string;
  cell: Cell;
  /** Persisted Arrange-mode offset from home. Defaults to (0,0). */
  offset?: Point;
  /** Settled card size; the clearance pass needs it. Defaults applied by callers. */
  size?: Size;
}

/** An undirected edge (vein) between two node ids. */
export type Edge = readonly [string, string];

/** Default card size when a node has not been measured yet (matches theme-a). */
export const DEFAULT_NODE_SIZE: Size = { w: 180, h: 110 };
