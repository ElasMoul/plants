import { Edge } from './types';

/** Adjacency: node id → its neighbour ids, in insertion order (deterministic). */
export type Adjacency = Record<string, string[]>;

/** Breadth-first distances from the focus: node id → graph distance. */
export type RankMap = Record<string, number>;

/** The four ranks. Scale is spent on rank (C12): four card sizes. */
export type RankName = 'focus' | 'near' | 'far' | 'fringe';

/**
 * Build undirected adjacency from an edge list. Each edge contributes both
 * directions. `ids` seeds the map so isolated nodes (no edges) still appear with
 * an empty neighbour list — important so rank() can report them as unreachable.
 */
export function buildAdjacency(edges: readonly Edge[], ids: readonly string[] = []): Adjacency {
  const adj: Adjacency = {};
  for (const id of ids) adj[id] = [];
  const ensure = (id: string) => (adj[id] ??= []);
  for (const [a, b] of edges) {
    ensure(a).push(b);
    ensure(b).push(a);
  }
  return adj;
}

/**
 * RANK — breadth-first graph distance from the focus, recomputed on every hop
 * (C3/C12). Faithful to the theme-a `ranks()`: the focus is 0, its direct
 * neighbours 1, and so on. Nodes not reachable from the focus are absent from
 * the map (callers treat "absent" as the fringe — see rankNameFor).
 */
export function rank(focusId: string, adjacency: Adjacency): RankMap {
  const dist: RankMap = { [focusId]: 0 };
  const queue: string[] = [focusId];
  while (queue.length) {
    const cur = queue.shift() as string;
    for (const n of adjacency[cur] ?? []) {
      if (dist[n] === undefined) {
        dist[n] = dist[cur] + 1;
        queue.push(n);
      }
    }
  }
  return dist;
}

/** Map a graph distance to a rank name: 0 focus, 1 near, 2 far, ≥3 fringe. */
export function rankName(distance: number): RankName {
  if (distance <= 0) return 'focus';
  if (distance === 1) return 'near';
  if (distance === 2) return 'far';
  return 'fringe';
}

/**
 * Rank name for a specific node given a RankMap. An unreachable node (absent
 * from the map) is the fringe — the same treatment theme-a's present() gives a
 * distance of 9.
 */
export function rankNameFor(id: string, ranks: RankMap): RankName {
  const d = ranks[id];
  return d === undefined ? 'fringe' : rankName(d);
}

/** Direct neighbours of the focus (rank 1), in deterministic adjacency order. */
export function neighboursOf(focusId: string, adjacency: Adjacency): string[] {
  return [...(adjacency[focusId] ?? [])];
}
