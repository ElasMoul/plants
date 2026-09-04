import { computed, inject, Injectable, signal } from '@angular/core';
import {
  Adjacency,
  anchorPosition,
  buildAdjacency,
  buildRoute,
  Camera,
  cameraForPoint,
  DEFAULT_LATTICE,
  computeTargets,
  easeOutCubic,
  HOP_DURATION_MS,
  Point,
  rank,
  RankName,
  rankNameFor,
  shortestPath,
  Size,
  TargetMap,
  travelCamera,
} from '@plantpal/rhizome-engine';
import { DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import { timeLabel } from './dates';
import type { WorldSources } from './world.dto';
import { FIXTURE_WORLD } from './world.fixture';
import { NodeKind, WorldData, WorldNode } from './world.model';

/** Overall load status of the live world. Degradation itself is per-node (C22-C25). */
export type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/** Nominal card size per rank (from the tokens' four card widths). Measured
 *  feedback from the DOM can refine these later; nominal keeps layout deterministic. */
const RANK_SIZE: Record<RankName, Size> = {
  focus: { w: 436, h: 300 },
  near: { w: 300, h: 190 },
  far: { w: 240, h: 150 },
  fringe: { w: 180, h: 110 },
};

/** Initial camera scale — the focus and its neighbourhood fill the frame. */
const INITIAL_K = 1;

export const LAYOUT_KEY = 'atlas_layout';

/** What "keep where I put things" actually keeps. */
export interface StoredLayout {
  cells: Record<string, { col: number; row: number }>;
  offsets: Record<string, Point>;
  modes: Record<string, 'min' | 'auto' | 'full'>;
}

function readLayout(): string | null {
  try {
    return localStorage.getItem(LAYOUT_KEY);
  } catch {
    return null;
  }
}

export function parseLayout(raw: string | null): StoredLayout {
  const empty: StoredLayout = { cells: {}, offsets: {}, modes: {} };
  if (!raw) return empty;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return empty;
  }
  if (!parsed || typeof parsed !== 'object') return empty;
  const r = parsed as Record<string, unknown>;
  const out: StoredLayout = { cells: {}, offsets: {}, modes: {} };
  for (const [id, v] of Object.entries((r['cells'] ?? {}) as Record<string, unknown>)) {
    const c = v as { col?: unknown; row?: unknown };
    if (typeof c?.col === 'number' && typeof c?.row === 'number') out.cells[id] = { col: c.col, row: c.row };
  }
  for (const [id, v] of Object.entries((r['offsets'] ?? {}) as Record<string, unknown>)) {
    const p = v as { x?: unknown; y?: unknown };
    if (typeof p?.x === 'number' && typeof p?.y === 'number') out.offsets[id] = { x: p.x, y: p.y };
  }
  for (const [id, v] of Object.entries((r['modes'] ?? {}) as Record<string, unknown>)) {
    if (v === 'min' || v === 'auto' || v === 'full') out.modes[id] = v;
  }
  return out;
}

@Injectable({ providedIn: 'root' })
export class WorldStore {
  private readonly settings = inject(SettingsStore);
  private readonly device = inject(DeviceStore);
  /** What the last session kept, when it was allowed to keep anything. */
  private readonly storedLayout: StoredLayout = this.settings.settings().privacy.rememberLayout
    ? parseLayout(readLayout())
    : { cells: {}, offsets: {}, modes: {} };

  private readonly data = signal<WorldData>(FIXTURE_WORLD);

  readonly nodes = computed<WorldNode[]>(() => this.data().nodes);
  /** From the live assembly (undefined on the fixture). */
  readonly latestFailedScanId = computed(() => this.data().latestFailedScanId);
  /** Loader facts beside the board — reminders, due rows, treatment index (C9 reads). */
  readonly meta = computed(() => this.data().meta);
  /** A disease description is still being written — polled like a pending scan. */
  readonly hasPendingDescription = computed(() => !!this.data().meta?.hasPendingDescription);
  readonly edges = computed(() => this.data().edges);
  readonly order = computed<string[]>(() => this.nodes().map(n => n.id));
  private readonly adjacency = computed<Adjacency>(() => buildAdjacency(this.edges(), this.order()));
  private readonly nodeById = computed<Record<string, WorldNode>>(() =>
    Object.fromEntries(this.nodes().map(n => [n.id, n])),
  );

  readonly focusId = signal<string>(FIXTURE_WORLD.initialFocus);

  /** Breadth-first rank from the focus, recomputed on every focus change (C3). */
  readonly ranks = computed(() => rank(this.focusId(), this.adjacency()));

  /** Measured card sizes from the DOM (measureBoxes) — fringe is width:auto. */
  private readonly measured = signal<Record<string, Size>>({});

  /** Card size per node: measured when available, nominal by rank otherwise. */
  readonly sizes = computed<Record<string, Size>>(() => {
    const ranks = this.ranks();
    const measured = this.measured();
    const out: Record<string, Size> = {};
    for (const n of this.nodes()) out[n.id] = measured[n.id] ?? RANK_SIZE[rankNameFor(n.id, ranks)];
    return out;
  });

  /**
   * Feed freshly measured sizes into the clearance pass and settle the board on
   * the corrected targets, then fit the focus (zoom yields so it never clips —
   * floor 0.28, mirrors --vs-card-reach/--vs-card-air).
   */
  applyMeasuredSizes(sizes: Record<string, Size>, viewportHeight: number): void {
    this.measured.set(sizes);
    this.rendered.set(this.targets());
    const f = sizes[this.focusId()];
    const room = viewportHeight * 0.96 - 80;
    const k = f && f.h > room ? Math.max(0.28, room / f.h) : 1;
    this.frameFocus(k);
  }

  /** Target position per node — the clearance pass over the current focus (C7). */
  readonly targets = computed<TargetMap>(() => {
    const sizes = this.sizes();
    const nodes: Record<string, { anchor: Point; size: Size }> = {};
    for (const n of this.nodes()) {
      nodes[n.id] = { anchor: anchorPosition({ cell: n.cell, offset: this.offsets()[n.id] ?? n.offset }, this.lattice()), size: sizes[n.id] };
    }
    return computeTargets({
      focusId: this.focusId(),
      order: this.order(),
      nodes,
      adjacency: this.adjacency(),
      dragMode: this.dragMode(),
    });
  });

  /**
   * The positions actually drawn this frame. Idle: equal to targets(). During a
   * hop: tweened from the pre-hop positions to the new targets, in lockstep with
   * the camera, so the world never tears in two (C10/C11). Read by positionOf().
   */
  private readonly rendered = signal<Record<string, Point>>({});

  /** True while a hop's animation is in flight (a second hop cancels the first). */
  readonly travelling = signal(false);
  private rafId = 0;

  /** Screen-reader announcement of the last hop (drawn into a polite live region). */
  readonly announcement = signal('');

  /** The hop destination while its content is still arriving (wears the skeleton). */
  readonly expandingId = signal<string | null>(null);

  /** The path travelled (crumbs). Truncates on backtrack — never grows a loop. */
  readonly path = signal<string[]>([FIXTURE_WORLD.initialFocus]);

  /** The vein currently hinted (Navigate-to hover / keyboard cursor). */
  readonly hintedVein = signal<readonly [string, string] | null>(null);

  /** Degradation probes ("Show this screen") — material on the board, per node. */
  readonly probeSlow = signal(false);
  readonly probeOffline = signal(false);
  readonly probeReduced = signal(false);

  /** App vs overview (settings) view. */
  readonly mode = signal<'app' | 'overview'>('app');

  /** Arrange mode: anchors only, everything else inert. */
  readonly dragMode = signal(false);
  private savedCamera: Camera | null = null;

  /** User drag offsets (Arrange) — the persisted layer of the position formula. */
  readonly offsets = signal<Record<string, Point>>({});

  /** The live lattice (interface switch re-pitches; cells never change). */
  readonly lattice = signal(DEFAULT_LATTICE);

  /** Interface + palette (Settings · Appearance). */
  readonly ui = signal<'sill-line' | 'glasshouse-table'>('sill-line');
  readonly palette = signal('first-light');

  setArrange(on: boolean): void {
    if (on === this.dragMode()) return;
    if (on) {
      this.savedCamera = this.camera();
      this.dragMode.set(true);
      this.say('Arrange mode. Drag any card anywhere; its position is kept.');
    } else {
      this.dragMode.set(false);
      if (this.savedCamera) this.camera.set(this.savedCamera);
      this.say('Left arrange mode. Positions kept.');
    }
    this.layoutEpoch.update(v => v + 1);
  }

  setOffset(id: string, offset: Point): void {
    this.offsets.update(o => ({ ...o, [id]: offset }));
    this.persistLayout();
  }

  hasOffset(id: string): boolean {
    const o = this.offsets()[id];
    return !!o && (o.x !== 0 || o.y !== 0);
  }

  setUI(ui: 'sill-line' | 'glasshouse-table'): void {
    this.ui.set(ui);
    document.documentElement.setAttribute('data-ui', ui);
    // choosing an interface brings its default palette with it (coupled reading)
    const pal = ui === 'sill-line' ? 'first-light' : 'glasshouse-table';
    this.setPalette(pal);
    // re-read the pitch the new interface declares; cells never change (C7/C8)
    const cs = getComputedStyle(document.documentElement);
    const px = parseFloat(cs.getPropertyValue('--vs-pitch-x')) || DEFAULT_LATTICE.pitchX;
    const py = parseFloat(cs.getPropertyValue('--vs-pitch-y')) || DEFAULT_LATTICE.pitchY;
    this.lattice.set({ ...DEFAULT_LATTICE, pitchX: px, pitchY: py });
    this.layoutEpoch.update(v => v + 1);
    this.frameFocus();
  }

  setPalette(palette: string): void {
    this.palette.set(palette);
    document.documentElement.setAttribute('data-palette', palette);
  }

  /**
   * ▢ Fit to screen: the focused node fills the viewport top-to-bottom. A node
   * smaller than the screen is NOT forced larger — k never exceeds 1.
   */
  fitFocusScreen(viewportHeight: number): void {
    const h = this.sizes()[this.focusId()]?.h ?? 0;
    const room = viewportHeight * 0.96 - 80;
    const k = h > room ? Math.max(0.28, room / h) : 1;
    this.frameFocus(k);
  }

  /** Escape: one step back along the crumb path. */
  goBack(): void {
    const p = this.path();
    if (p.length > 1) this.go(p[p.length - 2]);
  }

  /** The pinned prototype's own set — ids that only exist on the fixture board. */
  private static readonly FIXTURE_SLOW = [
    'n-garden', 'n-garden-more', 'n-platform', 'n-journal', 'n-species-more',
  ];
  /** The hubs of a live board, so the slow probe is not inert on real data. */
  private static readonly HUB_SLOW = [
    'n-garden', 'n-garden-more', 'n-platform', 'n-journal', 'n-reminders', 'n-treatments', 'n-today',
  ];

  /** Which nodes the slow probe holds "still arriving" (Settings · Advanced). */
  readonly slowNodes = computed(
    () =>
      new Set(
        this.settings.settings().advanced.slowNodes === 'fixture'
          ? WorldStore.FIXTURE_SLOW
          : WorldStore.HUB_SLOW,
      ),
  );

  isPending(id: string): boolean {
    if (this.probeSlow() && this.slowNodes().has(id)) return true;
    return this.nodeById()[id]?.state === 'loading';
  }

  /** Where every node sits right now — fed back into the next layout so an existing
   *  node keeps its cell and a new one takes a free one (C8). */
  cellsSnapshot(): Record<string, { col: number; row: number }> {
    const out: Record<string, { col: number; row: number }> = { ...this.storedLayout.cells };
    for (const n of this.nodes()) out[n.id] = { col: n.cell.col, row: n.cell.row };
    return out;
  }

  /** The raw sources the last load assembled from — read by the account's export. */
  readonly lastSources = signal<WorldSources | null>(null);

  /** The wall-clock moment this board was last true — the offline bar's own fact. */
  readAtLabel(): string {
    return timeLabel(this.data().meta?.syncedAt ?? new Date().toISOString());
  }

  /** Veins between here and there — the number an arrival announces before travelling. */
  distanceTo(id: string): number {
    const chain = shortestPath(this.focusId(), id, this.adjacency());
    return chain.length ? chain.length - 1 : -1;
  }

  /** Chrome-originated announcement (say()). Clears itself after the configured
   *  delay — zero keeps the last sentence, reduced motion is given longer to read. */
  private announceTimer: ReturnType<typeof setTimeout> | null = null;

  say(message: string): void {
    this.announcement.set(message);
    if (this.announceTimer) clearTimeout(this.announceTimer);
    this.announceTimer = null;
    const ms = this.settings.settings().general.announceMs;
    if (ms <= 0) return;
    const wait = this.prefersReducedMotion() ? Math.max(ms, 2600) : ms;
    this.announceTimer = setTimeout(() => this.announcement.set(''), wait);
  }

  /** Fit the whole world in view: extent = lattice ∪ live positions, padded. */
  fitAll(viewportW: number, viewportH: number): void {
    const t = this.targets();
    let minX = 0, minY = 0, maxX = 3600, maxY = 1980;
    for (const id of this.order()) {
      const p = t[id];
      minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
      maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
    }
    minX -= 160; minY -= 160; maxX += 160; maxY += 160;
    const k = Math.max(0.28, Math.min(viewportW / (maxX - minX), viewportH / (maxY - minY), 1));
    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    this.camera.set(cameraForPoint({ x: cx, y: cy }, k, this.screenCentre()));
  }

  /** Per-card size pins: MIN / AUTO / FULL (survive hops — theme-a rec.mode). */
  readonly modes = signal<Record<string, 'min' | 'auto' | 'full'>>({});

  /** Bumped whenever card sizes may have changed outside a hop (mode pins). */
  readonly layoutEpoch = signal(0);

  modeOf(id: string): 'min' | 'auto' | 'full' {
    return this.modes()[id] ?? 'auto';
  }

  setModeFor(id: string, mode: 'min' | 'auto' | 'full'): void {
    this.modes.update(m => ({ ...m, [id]: mode }));
    this.persistLayout();
    this.layoutEpoch.update(v => v + 1); // a card that grew must not land on a neighbour
  }

  /** A mutation stake was pressed — it changes data, never the camera (theme-a). */
  announceStake(label: string, nodeName: string): void {
    this.announcement.set(`“${label}” recorded on ${nodeName}. The camera did not move.`);
  }

  /** The camera over the plane. */
  readonly camera = signal<Camera>({ x: 0, y: 0, k: INITIAL_K });

  /** Screen centre of the free box (viewport, minus chrome). Set by the shell. */
  readonly screenCentre = signal<Point>({ x: 640, y: 360 });

  /** Live-load status. The board renders the fixture until (and unless) live data lands. */
  readonly loadState = signal<LoadState>('idle');

  /**
   * Replace the world with freshly-assembled live data. The geography is recomputed
   * from the new nodes; the camera reframes the new initial focus. If a live load
   * fails the fixture simply stays — the board never blanks (no global banner).
   */
  setWorld(data: WorldData): void {
    this.data.set(data);
    const focus = this.initialFocusFor(data);
    this.focusId.set(focus);
    this.path.set([focus]);
    this.rendered.set(this.targets());
    this.loadState.set('ready');
    this.persistLayout();
    this.frameFocus();
  }

  /** Where the world opens (Settings · General): the garden, what is due, or
   *  the place this device last remembered — each only when it really exists. */
  private initialFocusFor(data: WorldData): string {
    const has = (id: string | undefined): id is string => !!id && data.nodes.some(n => n.id === id);
    const s = this.settings.settings();
    if (s.general.initialFocus === 'today' && has('n-today')) return 'n-today';
    if (s.general.initialFocus === 'last' && s.privacy.rememberLastFocus) {
      const last = this.device.state().lastFocus;
      if (has(last)) return last;
    }
    return data.initialFocus;
  }

  /**
   * Refresh the world with newly-polled data WITHOUT moving the user: the focus
   * (and path) survive when their nodes still exist; content updates in place —
   * an arrival never moves the camera (C9).
   */
  updateWorld(data: WorldData): void {
    const focus = this.focusId();
    this.data.set(data);
    if (!data.nodes.some(n => n.id === focus)) {
      this.focusId.set(data.initialFocus);
      this.path.set([data.initialFocus]);
    } else {
      this.path.update(p => p.filter(id => data.nodes.some(n => n.id === id)));
    }
    this.rendered.set(this.targets());
    this.loadState.set('ready');
    this.persistLayout();
  }

  /** Neighbours of the current focus with their nodes, for the Navigate-to rail. */
  focusNeighbours(): { id: string; name: string; recap: string }[] {
    const byId = this.nodeById();
    return (this.adjacency()[this.focusId()] ?? []).map(id => ({
      id,
      name: byId[id]?.name ?? id,
      recap: byId[id]?.unknown ? 'not fetched yet' : byId[id]?.recap ?? '',
    }));
  }

  markLoading(): void {
    this.loadState.set('loading');
  }

  markError(): void {
    this.loadState.set('error');
  }

  /** CSS transform string for the plane. */
  readonly planeTransform = computed(() => {
    const c = this.camera();
    return `translate(${c.x}px, ${c.y}px) scale(${c.k})`;
  });

  rankNameOf(id: string): RankName {
    return rankNameFor(id, this.ranks());
  }

  /**
   * Where a card is drawn. Idle, this IS the live targets() — so an arrange-mode
   * drag (offsets → anchors), a probe, or a size pin moves the card and its
   * veins in the same frame, exactly like the prototype's place(). The rendered
   * snapshot is read only while a hop's tween is in flight.
   */
  positionOf(id: string): Point {
    if (this.travelling()) {
      return this.rendered()[id] ?? this.targets()[id] ?? { x: 0, y: 0 };
    }
    return this.targets()[id] ?? { x: 0, y: 0 };
  }

  kindOf(id: string): NodeKind | undefined {
    return this.nodeById()[id]?.kind;
  }

  isFocus(id: string): boolean {
    return id === this.focusId();
  }

  /** Frame the current focus at the screen centre (no travel animation — C4 adds that). */
  frameFocus(k: number = this.camera().k): void {
    const at = this.positionOf(this.focusId());
    this.camera.set(cameraForPoint(at, k, this.screenCentre()));
  }

  constructor() {
    // The remembered reading is re-applied so the interface's pitch is re-read
    // (applyBootAppearance only painted the attributes). Cells never change.
    const look = this.settings.settings().appearance;
    this.setUI(look.ui);
    this.setPalette(look.palette);
    // Kept positions and size pins come back before the first frame.
    this.offsets.set({ ...this.storedLayout.offsets });
    this.modes.set({ ...this.storedLayout.modes });
    // Idle: what we draw is the settled clearance layout.
    this.rendered.set(this.targets());
  }

  /**
   * Persist cells, dragged offsets and size pins — but only while the reader
   * allows it; turning the setting off removes the key rather than freezing it.
   */
  private layoutTimer: ReturnType<typeof setTimeout> | null = null;

  persistLayout(): void {
    if (!this.settings.settings().privacy.rememberLayout) {
      this.forgetLayout();
      return;
    }
    if (this.layoutTimer) clearTimeout(this.layoutTimer);
    this.layoutTimer = setTimeout(() => {
      const payload: StoredLayout = {
        cells: this.cellsSnapshot(),
        offsets: this.offsets(),
        modes: this.modes(),
      };
      try {
        localStorage.setItem(LAYOUT_KEY, JSON.stringify(payload));
      } catch {
        /* storage is a convenience, never a requirement */
      }
    }, 250);
  }

  forgetLayout(): void {
    if (this.layoutTimer) clearTimeout(this.layoutTimer);
    this.layoutTimer = null;
    try {
      localStorage.removeItem(LAYOUT_KEY);
    } catch {
      /* storage is a convenience, never a requirement */
    }
  }

  /**
   * Travel to a node. Recompute rank/clearance around it, then animate the camera
   * ALONG the vein polyline (the shortest path) while the cards tween to their new
   * places — one timing (HOP_DURATION_MS), one code path (C10/C11). Under reduced
   * motion it settles immediately. A second hop cancels the first.
   */
  go(id: string): void {
    if (this.dragMode()) return; // arrange mode: nothing travels
    if (id === this.focusId() || this.travelling() || !this.nodeById()[id]) return;
    const from = this.focusId();
    const chain = shortestPath(from, id, this.adjacency());
    if (chain.length < 2) return; // unreachable — nothing is faked

    this.focusId.set(id); // recomputes ranks + targets
    if (this.settings.settings().privacy.rememberLastFocus) this.device.setLastFocus(id);
    // crumbs: truncate on backtrack, else extend (never grows a loop)
    this.path.update(p => {
      const at = p.indexOf(id);
      return at >= 0 ? p.slice(0, at + 1) : [...p, id];
    });
    const dest = this.nodeById()[id];
    const veins = this.adjacency()[id]?.length ?? 0;
    this.announcement.set(`Travelled to ${dest.name}. ${veins} veins from here.`);
    const targets = this.targets();
    const centre = this.screenCentre();
    const k0 = this.camera().k;
    const k1 = INITIAL_K;

    // The destination wears its skeleton while the hop is in flight (C22 —
    // layout was already computed against its settled size, never the skeleton).
    this.expandingId.set(id);

    if (this.prefersReducedMotion()) {
      this.rendered.set(targets);
      this.camera.set(cameraForPoint(targets[id], k1, centre));
      this.expandingId.set(null);
      return;
    }

    const start = { ...this.rendered() };
    const route = buildRoute(chain.map(cid => targets[cid]));
    const order = this.order();
    const t0 = performance.now();
    cancelAnimationFrame(this.rafId);
    this.travelling.set(true);

    const step = (now: number): void => {
      const p = Math.min(1, (now - t0) / HOP_DURATION_MS);
      const e = easeOutCubic(p);
      const frame: Record<string, Point> = {};
      for (const nid of order) {
        const s = start[nid] ?? targets[nid];
        const tg = targets[nid];
        frame[nid] = { x: s.x + (tg.x - s.x) * e, y: s.y + (tg.y - s.y) * e };
      }
      this.rendered.set(frame);
      this.camera.set(travelCamera(route, centre, k0, k1, p));
      if (p < 1) {
        this.rafId = requestAnimationFrame(step);
      } else {
        this.rendered.set(targets);
        this.camera.set(cameraForPoint(targets[id], k1, centre));
        this.expandingId.set(null); // real content, only now expanded
        this.travelling.set(false);
      }
    };
    this.rafId = requestAnimationFrame(step);
  }

  private prefersReducedMotion(): boolean {
    if (this.probeReduced()) return true; // the probe always wins
    if (!this.settings.settings().appearance.followSystemMotion) return false;
    return (
      typeof matchMedia !== 'undefined' &&
      matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }

  /**
   * Travel along a vein. From the focus that is one hop to the other end; from
   * anywhere else it heads to the nearer end first (same go() as every hop, so
   * the camera still crosses every vein in between — C1/C10). Faithful to
   * theme-a travelAlongVein.
   */
  travelAlongVein(a: string, b: string): void {
    const focus = this.focusId();
    let to: string;
    if (a === focus) to = b;
    else if (b === focus) to = a;
    else {
      const d = this.ranks();
      const da = d[a] ?? 99;
      const db = d[b] ?? 99;
      to = da <= db ? a : b;
    }
    if (to !== focus) this.go(to);
  }

  /** Zoom the camera by a factor, keeping the focus framed. */
  zoomBy(factor: number): void {
    const k = Math.min(2.5, Math.max(0.15, this.camera().k * factor));
    this.frameFocus(k);
  }

  setScreenCentre(centre: Point): void {
    this.screenCentre.set(centre);
  }
}
