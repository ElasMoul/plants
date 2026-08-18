import { buildAdjacency, Edge, rank } from '@plantpal/rhizome-engine';
import { PlantDto, SpeciesDto, WorldSources } from './world.dto';
import { NodeKind, WorldData, WorldNode } from './world.model';

/** A collection with this many members or more collapses to 2 + "+N more" (C4/density). */
const DENSITY_CAP = 4;
const CENTER_ROW = 6;

type DraftNode = Omit<WorldNode, 'cell'> & { cell?: WorldNode['cell'] };

/**
 * Assemble the world graph from live backend data, entirely client-side. The
 * result is deterministic in its inputs: stable node ids, density collapse, and a
 * breadth-first cell layout so the same data always yields the same geography (C7).
 */
export function assembleWorld(sources: WorldSources): WorldData {
  const { dashboard, plants, species } = sources;
  const nodes: DraftNode[] = [];
  const edges: Edge[] = [];
  const add = (n: DraftNode) => nodes.push(n);
  const link = (a: string, b: string) => edges.push([a, b]);

  const hs = dashboard.healthSummary;
  const needWater = plants.filter(p => (p.nextWaterDays ?? 99) <= 0).length;

  // --- the hub: the user's garden (initial focus) ---
  add({ id: 'n-garden', kind: 'collection', kindLabel: 'Garden', name: 'My garden',
    recap: `${hs.totalPlants} plants · ${needWater} need water` });

  // account + today + reminders + species + problems hang off the garden
  add({ id: 'n-account', kind: 'platform', kindLabel: 'Account', name: 'Your account',
    recap: `Signed in · ${hs.totalPlants} plants` });
  link('n-account', 'n-garden');

  add({ id: 'n-today', kind: 'guide', kindLabel: 'Dashboard', name: 'Today',
    recap: `${dashboard.todayReminders.length} due · ${dashboard.overdueReminders.length} overdue` });
  link('n-garden', 'n-today');

  const reminderState = dashboard.todayReminders.length + dashboard.overdueReminders.length === 0 ? 'empty' : 'ready';
  add({ id: 'n-reminders', kind: 'journal', kindLabel: 'Reminders', name: 'Reminders',
    recap: reminderState === 'empty' ? 'Nothing due' : `${dashboard.overdueReminders.length} overdue`,
    state: reminderState });
  link('n-garden', 'n-reminders');
  link('n-today', 'n-reminders');

  add({ id: 'n-species', kind: 'collection', kindLabel: 'Collection', name: 'Species',
    recap: `${dashboard.speciesCount} species`, state: dashboard.speciesCount === 0 ? 'empty' : 'ready' });
  link('n-garden', 'n-species');

  if (hs.issuesCount > 0) {
    add({ id: 'n-problems', kind: 'problem', kindLabel: 'Problems', name: 'Problems',
      recap: `${hs.issuesCount} active` });
    link('n-garden', 'n-problems');
  }

  // --- plants under the garden, density-collapsed ---
  const rankedPlants = [...plants].sort(plantByOwed);
  emitCollapsed(rankedPlants, 'n-garden', {
    kind: 'plant', kindLabel: 'Plant', idPrefix: 'n-plant-', aggregateId: 'n-garden-more',
    aggregateName: 'more plants',
    toNode: p => ({ id: `n-plant-${p.id}`, kind: 'plant', kindLabel: 'Plant', name: p.nickname,
      recap: plantRecap(p), recapNote: p.commonName ?? p.species ?? undefined,
      state: p.healthStatus === 'UNKNOWN' ? 'unknown' : 'ready' }),
  }, add, link);

  // --- species under the species collection, density-collapsed ---
  const rankedSpecies = [...species].sort((a, b) => a.id - b.id);
  emitCollapsed(rankedSpecies, 'n-species', {
    kind: 'species', kindLabel: 'Species', idPrefix: 'n-species-', aggregateId: 'n-species-more',
    aggregateName: 'more species',
    toNode: s => ({ id: `n-species-${s.id}`, kind: 'species', kindLabel: 'Species',
      name: s.commonName ?? s.scientificName, recap: s.scientificName, recapNote: s.commonName ? s.scientificName : undefined }),
  }, add, link);

  layoutCells(nodes, edges, 'n-garden');
  return { nodes: nodes as WorldNode[], edges, initialFocus: 'n-garden' };
}

function plantByOwed(a: PlantDto, b: PlantDto): number {
  const score = (p: PlantDto) => (p.healthStatus === 'ISSUES_DETECTED' ? -1000 : 0) + (p.nextWaterDays ?? 99);
  return score(a) - score(b) || a.id - b.id;
}

function plantRecap(p: PlantDto): string {
  if (p.healthStatus === 'ISSUES_DETECTED') return 'Needs attention';
  const d = p.nextWaterDays;
  if (d == null) return 'Healthy';
  if (d <= 0) return `Needs water · ${-d}d overdue`;
  return `Water in ${d}d`;
}

interface CollapseSpec<T> {
  kind: NodeKind;
  kindLabel: string;
  idPrefix: string;
  aggregateId: string;
  aggregateName: string;
  toNode: (item: T) => DraftNode;
}

/**
 * Density rule: under four members draw them all; four or more draw the two
 * highest-ranked plus one traversable "+N more" aggregate node (C4). `ranked` must
 * already be sorted most-important-first.
 */
function emitCollapsed<T>(
  ranked: T[],
  parentId: string,
  spec: CollapseSpec<T>,
  add: (n: DraftNode) => void,
  link: (a: string, b: string) => void,
): void {
  const drawn = ranked.length < DENSITY_CAP ? ranked : ranked.slice(0, 2);
  for (const item of drawn) {
    const node = spec.toNode(item);
    add(node);
    link(parentId, node.id);
  }
  if (ranked.length >= DENSITY_CAP) {
    const rest = ranked.length - 2;
    add({ id: spec.aggregateId, kind: 'collection', kindLabel: 'Collection',
      name: `${rest} ${spec.aggregateName}`, recap: `+${rest} more` });
    link(parentId, spec.aggregateId);
  }
}

/**
 * Deterministic cell layout: breadth-first from the root. Column = 2 × depth; row
 * centres each layer around CENTER_ROW. Same graph → same cells (C7). A node not
 * reachable from the root is parked in a far column rather than dropped.
 */
export function layoutCells(nodes: DraftNode[], edges: Edge[], rootId: string): void {
  const ids = nodes.map(n => n.id);
  const adjacency = buildAdjacency(edges, ids);
  const depth = rank(rootId, adjacency);

  const byDepth = new Map<number, string[]>();
  for (const id of ids) {
    const d = depth[id];
    if (d === undefined) continue;
    (byDepth.get(d) ?? byDepth.set(d, []).get(d)!).push(id);
  }
  const cellFor: Record<string, WorldNode['cell']> = {};
  for (const [d, layer] of byDepth) {
    layer.sort();
    const mid = Math.floor((layer.length - 1) / 2);
    layer.forEach((id, i) => {
      cellFor[id] = { col: 2 * d, row: CENTER_ROW + i - mid };
    });
  }
  // park anything unreachable off to the far right, stacked
  let parked = 0;
  for (const n of nodes) {
    if (cellFor[n.id]) {
      n.cell = cellFor[n.id];
    } else {
      n.cell = { col: 20, row: parked++ };
    }
  }
}
