import { Cell, LatticeConfig, LatticeNode, Point } from './types';

/**
 * The default lattice, mirroring tokens.css / the theme-a prototype:
 *   PITCH_X = 300, PITCH_Y = 180, ORIGIN_X = 200, ORIGIN_Y = 80.
 */
export const DEFAULT_LATTICE: LatticeConfig = {
  originX: 200,
  originY: 80,
  pitchX: 300,
  pitchY: 180,
};

/** The air (world px) every card keeps around itself in the clearance pass. */
export const CLEARANCE = 34;

/** The single navigation timing (ms). One value, every hop, every trigger (C10/C11). */
export const NAV_MS = 300;

/**
 * HOME — the lattice position of a cell. `origin + cell × pitch`. A formula, not
 * a simulation: no time, no order, no randomness. Determinism lives here (C7).
 */
export function homePosition(cell: Cell, config: LatticeConfig = DEFAULT_LATTICE): Point {
  return {
    x: config.originX + cell.col * config.pitchX,
    y: config.originY + cell.row * config.pitchY,
  };
}

/**
 * ANCHOR — home plus the node's own persisted drag offset. This is where a card
 * rests when it is not the focus and nothing is clearing it. Only two things
 * ever change it: the user dragging in Arrange mode (offset), and re-pitching
 * the lattice (config) — never new data or a status change (C8/C9).
 */
export function anchorPosition(
  node: Pick<LatticeNode, 'cell' | 'offset'>,
  config: LatticeConfig = DEFAULT_LATTICE,
): Point {
  const home = homePosition(node.cell, config);
  const offset = node.offset ?? { x: 0, y: 0 };
  return { x: home.x + offset.x, y: home.y + offset.y };
}

/** Parse a "col,row" cell string (the theme-a `data-cell` format). */
export function parseCell(raw: string): Cell {
  const [col, row] = raw.split(',').map(v => Number(v.trim()));
  return { col, row };
}

/** Euclidean distance between two points. */
export function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}
