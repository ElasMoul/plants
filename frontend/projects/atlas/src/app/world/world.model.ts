import { Cell, Edge } from '@plantpal/rhizome-engine';
import type { FamilyFailure, ReminderDto } from './world.dto';

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
  | 'failed' // a load failed — name the fact, time, and two ways forward
  | 'archived'; // a stopped or finished thing that stays readable — never a removal

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
  /** The thumb glyph (Unicode, never an image — nothing external ever loads). */
  glyph: string;
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
  /** Generated full-body HTML in the prototype material language (live data). */
  body?: string;
  /** World-level: an async identification is still in flight (drives polling). */
}

/**
 * What the loader learned, beside the drawn nodes: the raw care-loop rows the
 * chrome and the actions need (a bell count, a distance, a step id) without
 * re-parsing generated HTML. Never rendered directly.
 */
export interface WorldMeta {
  /** The instant this world was assembled from — every "when" word is measured from it. */
  syncedAt: string;
  reminders: ReminderDto[];
  dueReminders: { id: number; nextDueAt: string; plantId: number; label: string }[];
  plantsIndex: { id: number; nickname: string; lastScanId?: number }[];
  treatmentsIndex: Record<
    number,
    {
      plantId: number;
      status: string;
      planId?: number;
      nextStepId?: number;
      nextStepOrder?: number;
      paused: boolean;
    }
  >;
  /** plant id → its most recent identification id. */
  scansByPlant: Record<number, number>;
  /** A disease description is still being written (drives polling, like a scan). */
  hasPendingDescription: boolean;
  failures: FamilyFailure[];
}

export interface WorldData {
  /** True while an identification is PENDING/PROCESSING (client polls). */
  hasPendingScan?: boolean;
  /** The most recent FAILED identification, retryable via POST /retry. */
  latestFailedScanId?: number;
  nodes: WorldNode[];
  edges: Edge[];
  /** The node the camera starts on. */
  initialFocus: string;
  /** Loader facts beside the board (undefined on the fixture). */
  meta?: WorldMeta;
}
