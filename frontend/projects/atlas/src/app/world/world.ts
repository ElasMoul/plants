import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  HostListener,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '@plantpal/shared-core';
import { advanceField, Mote, seedField, Size } from '@plantpal/rhizome-engine';
import { environment } from '../../environments/environment';
import { NodeCard } from '../node/node-card';
import { classicLinkFor, classicLoginLink } from './interop';
import { WorldGraphService } from './world-graph.service';
import { WorldStore } from './world.store';

interface VeinLine {
  a: string;
  b: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  live: boolean;
  unknown: boolean;
}

/**
 * The world shell — emits the prototype's own frame (#shell > #world-wrap >
 * #world > #plane > svg#veins + article.n cards) so the extracted rhizome.css
 * applies verbatim (H1). Chrome beyond the camera bar arrives in H3.
 */
@Component({
  selector: 'rz-world',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NodeCard],
  template: `
    <canvas id="motes" aria-hidden="true"></canvas>
    <div id="motes-wash" aria-hidden="true"></div>

    <div id="shell">
      <canvas id="motes-app" aria-hidden="true"></canvas>

      <div id="world-wrap">
        <div
          id="world"
          role="application"
          tabindex="0"
          aria-label="PlantPal botanical network. Arrow keys move along the veins to a neighbour, Enter travels to it, Alt plus the scroll wheel zooms."
        >
          <div id="plane" [style.transform]="store.planeTransform()">
            <svg id="veins" viewBox="0 0 3600 1980" aria-hidden="true" preserveAspectRatio="none">
              <g>
                @for (v of veins(); track v.a + '::' + v.b) {
                  <path
                    class="vein"
                    [attr.data-live]="v.live ? true : null"
                    [attr.data-unknown]="v.unknown ? true : null"
                    [attr.d]="'M ' + v.x1 + ' ' + v.y1 + ' L ' + v.x2 + ' ' + v.y2"
                  />
                  <path
                    class="vein-hit"
                    [attr.d]="'M ' + v.x1 + ' ' + v.y1 + ' L ' + v.x2 + ' ' + v.y2"
                    (click)="store.travelAlongVein(v.a, v.b)"
                  >
                    <title>Travel along this vein</title>
                  </path>
                }
              </g>
              <g>
                @for (v of veins(); track v.a + '::' + v.b) {
                  <circle
                    class="vein-node"
                    r="3.2"
                    [attr.data-live]="v.live ? true : null"
                    [attr.cx]="(v.x1 + v.x2) / 2"
                    [attr.cy]="(v.y1 + v.y2) / 2"
                  />
                }
              </g>
            </svg>

            @for (n of store.nodes(); track n.id) {
              <rz-node
                [id]="n.id"
                [node]="n"
                [rank]="store.rankNameOf(n.id)"
                [focus]="store.isFocus(n.id)"
                [expanding]="store.expandingId() === n.id"
                [mode]="store.modeOf(n.id)"
                [style.left.px]="store.positionOf(n.id).x"
                [style.top.px]="store.positionOf(n.id).y"
                (click)="onCardClick(n.id, $event)"
                (act)="onAct(n.id, $event)"
                (setMode)="store.setModeFor(n.id, $event)"
              />
            }
          </div>
        </div>
      </div>

      <!-- CHROME (minimal until H3): the camera bar + live readout -->
      <div id="camera" class="chrome">
        <button class="ch-btn" type="button" (click)="store.zoomBy(0.7)">
          <span aria-hidden="true">⌕−</span> Zoom out
        </button>
        <button class="ch-btn" type="button" (click)="store.frameFocus()">
          <span aria-hidden="true">▢</span> Recentre
        </button>
        @if (!authed()) {
          <a class="ch-btn" [href]="signInUrl" style="width:auto">Sign in</a>
        } @else if (focusClassicLink()) {
          <a class="ch-btn" [href]="focusClassicLink()" target="_blank" rel="noopener" style="width:auto">
            Open in PlantPal
          </a>
        }
      </div>
      <p id="live" role="status" aria-live="polite" [attr.data-on]="store.announcement() ? true : null">
        {{ store.announcement() }}
      </p>
    </div>
  `,
})
export class World {
  protected readonly store = inject(WorldStore);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly graph = inject(WorldGraphService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly authed = computed(() => this.auth.isLoggedIn());
  protected readonly signInUrl = classicLoginLink(environment.classicAppUrl);

  protected readonly focusNode = computed(() =>
    this.store.nodes().find(n => n.id === this.store.focusId()),
  );
  protected readonly focusClassicLink = computed(() => {
    const f = this.focusNode();
    return f ? classicLinkFor(f, environment.classicAppUrl) : null;
  });

  protected readonly veins = computed<VeinLine[]>(() => {
    const focus = this.store.focusId();
    const pos = this.store.targets();
    const unknownIds = new Set(this.store.nodes().filter(n => n.unknown).map(n => n.id));
    return this.store.edges().map(([a, b]) => {
      const pa = pos[a] ?? { x: 0, y: 0 };
      const pb = pos[b] ?? { x: 0, y: 0 };
      return {
        a, b,
        x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y,
        live: a === focus || b === focus,
        unknown: unknownIds.has(a) || unknownIds.has(b),
      };
    });
  });

  private motes: Mote[] = [];
  private moteRaf = 0;

  constructor() {
    // Keep --cam-k on the root in sync — the focus card's max-height divides by it.
    effect(() => {
      document.documentElement.style.setProperty('--cam-k', String(this.store.camera().k));
    });
    // Re-measure and settle after every completed hop, and whenever a size pin
    // changes card heights (measured clearance, H1/H2).
    effect(() => {
      const focus = this.store.focusId();
      const travelling = this.store.travelling();
      this.store.layoutEpoch();
      if (focus && !travelling) {
        requestAnimationFrame(() => this.measureAndSettle());
      }
    });
    afterNextRender(() => {
      this.syncCentre();
      this.measureAndSettle();
      this.startMotes();
      this.loadLive();
    });
  }

  @HostListener('window:resize')
  protected onResize(): void {
    this.syncCentre();
    this.measureAndSettle();
    this.sizeMotes();
  }

  /**
   * measureBoxes(), faithfully: pin --cam-k to 1 and set body[data-measuring]
   * (rhizome.css kills transitions) so cards are read at their settled size —
   * fringe cards are width:auto, so sizes must be measured, never assumed.
   */
  private measureAndSettle(): void {
    const root = document.documentElement;
    const prev = root.style.getPropertyValue('--cam-k');
    root.style.setProperty('--cam-k', '1');
    document.body.dataset['measuring'] = '1';
    void document.body.offsetWidth;
    const vw = window.innerWidth || 1280;
    const vh = window.innerHeight || 720;
    const wcap = Math.max(600, vw * 1.2);
    const hcap = Math.max(400, vh * 1.2);
    const sizes: Record<string, Size> = {};
    for (const el of Array.from(this.host.nativeElement.querySelectorAll('rz-node'))) {
      const h = el as HTMLElement;
      sizes[h.id] = { w: Math.min(wcap, h.offsetWidth || 180), h: Math.min(hcap, h.offsetHeight || 110) };
    }
    delete document.body.dataset['measuring'];
    if (prev) root.style.setProperty('--cam-k', prev);
    this.store.applyMeasuredSizes(sizes, vh);
  }

  private syncCentre(): void {
    const w = window.innerWidth || 1280;
    const h = window.innerHeight || 720;
    this.store.setScreenCentre({ x: w / 2, y: h / 2 });
  }

  /** The deterministic particle field (engine B5) painted onto #motes. */
  private startMotes(): void {
    this.sizeMotes();
    const canvas = this.host.nativeElement.querySelector('#motes') as HTMLCanvasElement | null;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    const styles = getComputedStyle(document.documentElement);
    const mote = styles.getPropertyValue('--vs-mote').trim() || 'rgba(143,178,106,.55)';
    const link = styles.getPropertyValue('--vs-mote-link').trim() || 'rgba(143,178,106,.16)';
    const frame = (): void => {
      if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      const w = canvas.width;
      const h = canvas.height;
      advanceField(this.motes, w, h);
      ctx.clearRect(0, 0, w, h);
      ctx.strokeStyle = link;
      ctx.lineWidth = 0.6;
      for (let i = 0; i < this.motes.length; i++) {
        for (let j = i + 1; j < this.motes.length; j++) {
          const dx = this.motes[i].x - this.motes[j].x;
          const dy = this.motes[i].y - this.motes[j].y;
          const d2 = dx * dx + dy * dy;
          if (d2 < 15000) {
            ctx.globalAlpha = 1 - d2 / 15000;
            ctx.beginPath();
            ctx.moveTo(this.motes[i].x, this.motes[i].y);
            ctx.lineTo(this.motes[j].x, this.motes[j].y);
            ctx.stroke();
          }
        }
      }
      ctx.globalAlpha = 1;
      ctx.fillStyle = mote;
      for (const m of this.motes) {
        ctx.beginPath();
        ctx.arc(m.x, m.y, m.r, 0, Math.PI * 2);
        ctx.fill();
      }
      this.moteRaf = requestAnimationFrame(frame);
    };
    cancelAnimationFrame(this.moteRaf);
    this.moteRaf = requestAnimationFrame(frame);
    this.destroyRef.onDestroy(() => cancelAnimationFrame(this.moteRaf));
  }

  private sizeMotes(): void {
    const canvas = this.host.nativeElement.querySelector('#motes') as HTMLCanvasElement | null;
    if (!canvas) return;
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    this.motes = seedField(canvas.width, canvas.height);
  }

  /**
   * Card click, with the prototype's delegation rules: [data-goto] hops and
   * prose doc-links travel to THEIR target; stakes mutate (announce only, the
   * camera never moves); tools are inert to travel; anything else travels to
   * the card. The verbatim bodies (world.bodies.ts) carry the real data-goto
   * and .stake markup, so delegation is the single wiring point.
   */
  protected onCardClick(id: string, ev: Event): void {
    const t = ev.target as HTMLElement;
    const goto = t.closest<HTMLElement>('[data-goto]');
    if (goto) {
      ev.preventDefault();
      ev.stopPropagation();
      this.store.go(goto.dataset['goto'] as string);
      return;
    }
    const stake = t.closest<HTMLElement>('.stake');
    if (stake) {
      ev.stopPropagation();
      const name = this.store.nodes().find(n => n.id === id)?.name ?? id;
      this.store.announceStake(stake.textContent?.trim() ?? 'Action', name);
      return;
    }
    if (t.closest('.n__modes, .n__grip, a, button')) return;
    this.store.go(id);
  }

  private loadLive(): void {
    if (!this.authed()) return;
    this.store.markLoading();
    this.graph
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => this.store.setWorld(data),
        error: () => this.store.markError(),
      });
  }

  protected onAct(nodeId: string, way: string): void {
    if (/retry/i.test(way)) this.loadLive();
  }
}
