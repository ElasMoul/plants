import { computed, Injectable, signal } from '@angular/core';
import {
  Adjacency,
  buildAdjacency,
  Camera,
  cameraForPoint,
  computeTargets,
  Point,
  rank,
  RankName,
  rankNameFor,
  Size,
  TargetMap,
  anchorPosition,
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

  /** Travel to a node: recompute rank/clearance around it and reframe. */
  go(id: string): void {
    if (id === this.focusId() || !this.nodeById()[id]) return;
    this.focusId.set(id);
    this.frameFocus();
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
