import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '@plantpal/shared-core';
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
 * The world shell (rz-world). One viewport, one plane. Going somewhere is the
 * camera rescaling + translating the plane — nothing routes, nothing unmounts
 * (C1/C4). Veins are drawn before anyone travels them; each carries an invisible
 * wide hit stroke so the vein itself is clickable (C18). Chrome (zoom, you-are-
 * here) is flush furniture and never moves with the camera (C14).
 */
@Component({
  selector: 'rz-world',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NodeCard],
  template: `
    <div class="rz-world">
      <!-- THE PLANE — everything on it moves with the camera -->
      <div class="rz-plane" [style.transform]="store.planeTransform()">
        <svg class="rz-veins" aria-hidden="true">
          @for (v of veins(); track v.a + '::' + v.b) {
            <line
              class="vein"
              [attr.data-live]="v.live ? true : null"
              [attr.data-unknown]="v.unknown ? true : null"
              [attr.x1]="v.x1"
              [attr.y1]="v.y1"
              [attr.x2]="v.x2"
              [attr.y2]="v.y2"
            />
            <line
              class="vein-hit"
              [attr.x1]="v.x1"
              [attr.y1]="v.y1"
              [attr.x2]="v.x2"
              [attr.y2]="v.y2"
              (click)="store.travelAlongVein(v.a, v.b)"
            >
              <title>Travel along this vein</title>
            </line>
          }
        </svg>

        @for (n of store.nodes(); track n.id) {
          <rz-node
            [node]="n"
            [rank]="store.rankNameOf(n.id)"
            [focus]="store.isFocus(n.id)"
            [style.left.px]="store.positionOf(n.id).x"
            [style.top.px]="store.positionOf(n.id).y"
            (click)="store.go(n.id)"
            (act)="onAct(n.id, $event)"
          />
        }
      </div>

      <!-- CHROME — flush furniture, never travels with the camera (C14) -->
      <header class="rz-chrome rz-topbar">
        <span class="rz-brand">PlantPal · Botanical Network</span>
        <span class="rz-actions">
          <span class="rz-here">You are here: {{ focusName() }}</span>
          @if (focusClassicLink()) {
            <a class="rz-link" [href]="focusClassicLink()" target="_blank" rel="noopener">Open in PlantPal ↗</a>
          }
          @if (!authed()) {
            <a class="rz-link rz-signin" [href]="signInUrl">Sign in</a>
          }
        </span>
      </header>
      <div class="rz-chrome rz-camera">
        <button type="button" (click)="store.zoomBy(0.8)" aria-label="Zoom out">−</button>
        <button type="button" (click)="store.frameFocus()" aria-label="Recentre">◎</button>
        <button type="button" (click)="store.zoomBy(1.25)" aria-label="Zoom in">+</button>
      </div>
    </div>
  `,
  styles: [
    `
      .rz-world {
        position: fixed;
        inset: 0;
        overflow: hidden;
        background:
          radial-gradient(ellipse 90% 70% at 50% 45%, var(--vs-void, #0a0c0c), var(--vs-void-deep, #070909) 100%);
      }
      .rz-plane {
        position: absolute;
        left: 0;
        top: 0;
        transform-origin: 0 0;
        will-change: transform;
      }
      .rz-veins {
        position: absolute;
        left: 0;
        top: 0;
        overflow: visible;
        width: 0;
        height: 0;
        pointer-events: none;
      }
      .vein {
        stroke: var(--vs-vein, rgba(143, 178, 106, 0.28));
        stroke-width: 1;
        fill: none;
      }
      .vein[data-live] { stroke: var(--vs-vein-live, rgba(143, 178, 106, 0.7)); stroke-width: 1.5; }
      .vein[data-unknown] { stroke-dasharray: 4 5; }
      .vein-hit {
        stroke: transparent;
        stroke-width: 20;
        fill: none;
        pointer-events: stroke;
        cursor: pointer;
      }

      .rz-chrome {
        position: fixed;
        z-index: 10;
        font-family: var(--vs-chrome-face, monospace);
        color: var(--vs-chrome-ink, var(--vs-ink-muted));
        text-transform: uppercase;
        letter-spacing: 0.1em;
        font-size: var(--vs-rung-18, 0.72rem);
      }
      .rz-topbar {
        top: 0;
        left: 0;
        right: 0;
        display: flex;
        justify-content: space-between;
        padding: 12px 18px;
        background: linear-gradient(var(--vs-void-deep, #070909), transparent);
      }
      .rz-actions { display: flex; gap: 16px; align-items: center; }
      .rz-here { color: var(--vs-ink-second); }
      .rz-link {
        color: var(--vs-ink-muted);
        text-decoration: none;
        border-bottom: 1px solid transparent;
      }
      .rz-link:hover { color: var(--vs-ink); border-bottom-color: var(--vs-membrane-lit, currentColor); }
      .rz-signin { color: var(--vs-kind-species, var(--vs-ink)); }
      .rz-camera {
        bottom: 18px;
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        gap: 6px;
      }
      .rz-camera button {
        width: 34px;
        height: 34px;
        border: var(--vs-hair, 1px) solid var(--vs-membrane, rgba(255, 255, 255, 0.14));
        background: var(--vs-void, #0a0c0c);
        color: var(--vs-ink);
        border-radius: var(--vs-corner-card, 5px);
        cursor: pointer;
        font-size: 1rem;
        line-height: 1;
      }
      .rz-camera button:hover { border-color: var(--vs-ink-muted); }
    `,
  ],
})
export class World {
  protected readonly store = inject(WorldStore);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly graph = inject(WorldGraphService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** Shared session: a token in localStorage (set by either app) means signed in. */
  protected readonly authed = signal(this.auth.isLoggedIn());
  protected readonly signInUrl = classicLoginLink(environment.classicAppUrl);

  protected readonly focusNode = computed(() =>
    this.store.nodes().find(n => n.id === this.store.focusId()),
  );
  protected readonly focusName = computed(() => this.focusNode()?.name ?? '');
  /** "Open in PlantPal" deep-link for the current focus (null if no classic page). */
  protected readonly focusClassicLink = computed(() => {
    const f = this.focusNode();
    return f ? classicLinkFor(f, environment.classicAppUrl) : null;
  });

  /** Vein polylines in world coordinates, live-marked when incident to the focus. */
  protected readonly veins = computed<VeinLine[]>(() => {
    const focus = this.store.focusId();
    const pos = this.store.targets();
    const unknownIds = new Set(this.store.nodes().filter(n => n.unknown).map(n => n.id));
    return this.store.edges().map(([a, b]) => {
      const pa = pos[a] ?? { x: 0, y: 0 };
      const pb = pos[b] ?? { x: 0, y: 0 };
      return {
        a,
        b,
        x1: pa.x,
        y1: pa.y,
        x2: pb.x,
        y2: pb.y,
        live: a === focus || b === focus,
        unknown: unknownIds.has(a) || unknownIds.has(b),
      };
    });
  });

  constructor() {
    afterNextRender(() => {
      this.syncCentreAndFrame();
      this.loadLive();
    });
  }

  @HostListener('window:resize')
  protected onResize(): void {
    this.syncCentreAndFrame();
  }

  /**
   * Overlay live backend data on the fixture. On failure the fixture stays — the
   * board never blanks and there is no global error banner (C22-C25).
   */
  private loadLive(): void {
    // Not signed in → keep the fixture and offer sign-in; don't fetch (no session).
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

  /** A failure card's "way forward" was pressed. Retry re-loads; others are stubs. */
  protected onAct(nodeId: string, way: string): void {
    if (/retry/i.test(way)) this.loadLive();
    // other ways forward (e.g. "Pick manually") deep-link into the classic app in a
    // later pass; for now they are inert affordances.
  }

  private syncCentreAndFrame(): void {
    const el = this.host.nativeElement;
    // Fall back to the window (then a sane default) when the host hasn't been laid
    // out yet, so framing never collapses to a (0,0) centre.
    const w = el.clientWidth || window.innerWidth || 1280;
    const h = el.clientHeight || window.innerHeight || 720;
    this.store.setScreenCentre({ x: w / 2, y: h / 2 });
    this.store.frameFocus();
  }
}
