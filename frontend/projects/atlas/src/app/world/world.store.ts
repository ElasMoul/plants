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

  /** Card size per node, nominal by rank. */
  readonly sizes = computed<Record<string, Size>>(() => {
    const ranks = this.ranks();
    const out: Record<string, Size> = {};
    for (const n of this.nodes()) out[n.id] = RANK_SIZE[rankNameFor(n.id, ranks)];
    return out;
  });

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

  /** The camera over the plane. */
  readonly camera = signal<Camera>({ x: 0, y: 0, k: INITIAL_K });

  /** Screen centre of the free box (viewport, minus chrome). Set by the shell. */
  readonly screenCentre = signal<Point>({ x: 640, y: 360 });

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
    const targets = this.targets();
    const centre = this.screenCentre();
    const k0 = this.camera().k;
    const k1 = INITIAL_K;

    if (this.prefersReducedMotion()) {
      this.rendered.set(targets);
      this.camera.set(cameraForPoint(targets[id], k1, centre));
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
