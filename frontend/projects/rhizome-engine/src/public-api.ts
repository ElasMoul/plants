/*
 * Public API Surface of @plantpal/rhizome-engine.
 *
 * A framework-agnostic (no Angular, no DOM) TypeScript engine implementing the
 * computable parts of the Rhizome constitution: deterministic lattice geometry
 * (B1), rank as graph distance (B2), the clearance pass (B3), arc-length camera
 * travel (B4), and the particle field (B5). The Angular atlas app renders engine
 * state; it never re-implements the maths.
 */
export * from './lib/types';
export * from './lib/geometry';
export * from './lib/graph';
export * from './lib/clearance';
