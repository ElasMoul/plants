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
}

export interface WorldData {
  nodes: WorldNode[];
  edges: Edge[];
  /** The node the camera starts on. */
  initialFocus: string;
}
