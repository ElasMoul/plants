import { computed, Injectable, signal } from '@angular/core';
import {
  Adjacency,
  anchorPosition,
  buildAdjacency,
  buildRoute,
  Camera,
  cameraForPoint,
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

@Injectable({ providedIn: 'root' })
export class WorldStore {
  private readonly data = signal<WorldData>(FIXTURE_WORLD);

  readonly nodes = computed<WorldNode[]>(() => this.data().nodes);
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
      nodes[n.id] = { anchor: anchorPosition({ cell: n.cell, offset: n.offset }), size: sizes[n.id] };
    }
    return computeTargets({
      focusId: this.focusId(),
      order: this.order(),
      nodes,
      adjacency: this.adjacency(),
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

  /** Per-card size pins: MIN / AUTO / FULL (survive hops — theme-a rec.mode). */
  readonly modes = signal<Record<string, 'min' | 'auto' | 'full'>>({});

  /** Bumped whenever card sizes may have changed outside a hop (mode pins). */
  readonly layoutEpoch = signal(0);

  modeOf(id: string): 'min' | 'auto' | 'full' {
    return this.modes()[id] ?? 'auto';
  }

  setModeFor(id: string, mode: 'min' | 'auto' | 'full'): void {
    this.modes.update(m => ({ ...m, [id]: mode }));
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
    this.focusId.set(data.initialFocus);
    this.rendered.set(this.targets());
    this.loadState.set('ready');
    this.frameFocus();
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

  positionOf(id: string): Point {
    return this.rendered()[id] ?? this.targets()[id] ?? { x: 0, y: 0 };
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
    // Idle: what we draw is the settled clearance layout.
    this.rendered.set(this.targets());
  }

  /**
   * Travel to a node. Recompute rank/clearance around it, then animate the camera
   * ALONG the vein polyline (the shortest path) while the cards tween to their new
   * places — one timing (HOP_DURATION_MS), one code path (C10/C11). Under reduced
   * motion it settles immediately. A second hop cancels the first.
   */
  go(id: string): void {
    if (id === this.focusId() || this.travelling() || !this.nodeById()[id]) return;
    const from = this.focusId();
    const chain = shortestPath(from, id, this.adjacency());
    if (chain.length < 2) return; // unreachable — nothing is faked

    this.focusId.set(id); // recomputes ranks + targets
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
