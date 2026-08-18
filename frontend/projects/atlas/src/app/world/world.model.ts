import { Cell, Edge } from '@plantpal/rhizome-engine';

/** The kind of thing a node is — drives its signature colour (--vs-kind-*). */
export type NodeKind =
  | 'collection'
  | 'species'
  | 'plant'
  | 'guide'
  | 'problem'
  | 'journal'
  | 'platform'
  | 'region';

/**
 * Per-node state — degradation is material ON THE BOARD, per node, never a global
 * banner (C22-C25). The geography never degrades: a node is in its cell, at its
 * rank, before any content arrives.
 */
export type NodeState =
  | 'ready' // content present
  | 'loading' // data outstanding — show the node's own skeleton, no spinner
  | 'empty' // nothing here yet — dashed, offers a way to begin
  | 'unknown' // unfetched region — dashed, traversable, a real way to fetch it
  | 'failed'; // a load failed — name the fact, time, and two ways forward

/** A failure drawn inside the node it belongs to (C25). */
export interface NodeFailure {
  fact: string;
  time?: string;
  dataNote?: string;
  waysForward: string[];
}

/** A node as the atlas renders it: engine cell + presentation. */
export interface WorldNode {
  id: string;
  cell: Cell;
  offset?: { x: number; y: number };
  kind: NodeKind;
  /** The human label shown above the name (e.g. "Species", "Collection"). */
  kindLabel: string;
  name: string;
  recap: string;
  /** A recap's second line (dropped at the fringe). */
  recapNote?: string;
  /** Unknown/unfetched region — drawn dashed, traversable (C22-C25). */
  unknown?: boolean;
  /** Degradation state; defaults to 'ready'. */
  state?: NodeState;
  /** Present when state === 'failed'. */
  failure?: NodeFailure;
  /** Optional longer body shown when this node is the focus (D2 detail). */
  detail?: string[];
}

export interface WorldData {
  nodes: WorldNode[];
  edges: Edge[];
  /** The node the camera starts on. */
  initialFocus: string;
}
