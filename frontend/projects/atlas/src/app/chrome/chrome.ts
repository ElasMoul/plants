import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { AuthService } from '@plantpal/shared-core';
import { environment } from '../../environments/environment';
import { classicLoginLink } from '../world/interop';
import { WorldActionsService } from '../world/world-actions.service';
import { WorldStore } from '../world/world.store';
import { MOCK_MODE } from '../core/mock-mode';
import { SettingsStore } from '../settings/settings.store';
import { actionsFor } from './actions-for';

const MM_W = 208;
const MM_H = 104;

/**
 * The chrome — every piece of fixed furniture, emitting the prototype's own
 * markup so rhizome.css applies verbatim: topbar (brand, crumbs, search, bell,
 * account), app rail, Actions rail, Navigate-to rail, minimap + zoom stack,
 * camera bar, gesture legend, probe panel, offline bar. Chrome never moves with
 * the camera and never carries a tick (C14).
 */
@Component({
  selector: 'rz-chrome',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div id="offline-bar" role="status">
      <span>Offline · showing the world as it was at 09:12 · changes will be queued</span>
    </div>

    <header id="topbar" class="chrome">
      <span class="mark"><span class="glyph" aria-hidden="true">❧</span> PlantPal</span>
      <span class="sub">{{ mock?.enabled ? 'Botanical Network · mock garden' : 'Botanical Network' }}</span>
      <span class="spacer"></span>
      <div id="here">
        <span class="label">You are here</span>
        <nav id="crumbs" aria-label="Path travelled">
          @for (c of crumbs(); track c.id; let last = $last) {
            <button class="crumb" type="button" [attr.aria-current]="last ? 'true' : null" (click)="store.go(c.id)">
              {{ c.name }}</button>@if (!last) {<span aria-hidden="true"> › </span>}
          }
        </nav>
      </div>
      <span class="spacer"></span>
      <div class="ch-field" role="search">
        <span aria-hidden="true">⌕</span>
        <input id="search" type="search" placeholder="Search…" aria-label="Search the network" (keydown.enter)="onSearch($event)" />
      </div>
      <button class="ch-btn" id="bell" type="button" style="width:auto" (click)="onBell()">
        Notifications <span class="ch-count">2</span>
      </button>
      @if (authed()) {
        <button class="ch-btn" id="account" type="button" style="width:auto" aria-expanded="false" (click)="goIfThere('n-account')">{{ accountName() }}</button>
      } @else {
        <a class="ch-btn" id="account" style="width:auto" [href]="signInUrl()">Sign in</a>
      }
    </header>

    <nav id="rail" class="chrome" aria-label="Places in PlantPal">
      <button class="ch-btn ch-btn--square" type="button" aria-current="page" title="Network" (click)="onRecenter()"><span aria-hidden="true">❋</span><span class="sr">Network</span></button>
      <button class="ch-btn ch-btn--square" type="button" title="My garden" (click)="goIfThere('n-garden')"><span aria-hidden="true">♣</span><span class="sr">My garden</span></button>
      <button class="ch-btn ch-btn--square" type="button" title="Due today" (click)="goIfThere('n-reminders')"><span aria-hidden="true">◷</span><span class="sr">Due today</span></button>
      <button class="ch-btn ch-btn--square" type="button" title="Identify" (click)="goIfThere('n-ident')"><span aria-hidden="true">◎</span><span class="sr">Identify</span></button>
      <button class="ch-btn ch-btn--square" type="button" title="Journal" (click)="goJournal()"><span aria-hidden="true">▤</span><span class="sr">Journal</span></button>
      <button class="ch-btn ch-btn--square" type="button" id="open-settings" title="Settings" (click)="openSettings($event)"><span aria-hidden="true">⚙</span><span class="sr">Settings</span></button>
    </nav>

    <aside class="chrome side" id="actions" aria-labelledby="actions-h">
      <span class="chrome__title" id="actions-h">Actions · <b id="actions-scope">{{ focusName() }}</b></span>
      <div class="chrome__body" id="actions-body">
        @for (a of focusActions(); track a) {
          <button class="ch-btn ch-btn--mutate" type="button" (click)="onAction(a)">{{ ' ' + a }}</button>
        }
      </div>
      <span class="chrome__foot">These change data. They never move the camera.</span>
    </aside>

    <aside class="chrome side" id="navto" aria-labelledby="navto-h">
      <span class="chrome__title" id="navto-h">Navigate to · <b id="navto-degree">{{ degreeLabel() }}</b></span>
      <div class="chrome__body" id="navto-body">
        @for (n of neighbours(); track n.id) {
          <button
            class="ch-btn ch-btn--travel"
            type="button"
            (click)="store.go(n.id)"
            (pointerenter)="store.hintedVein.set([store.focusId(), n.id])"
            (pointerleave)="store.hintedVein.set(null)"
          >→ {{ n.name }} <small>{{ n.recap }}</small></button>
        }
      </div>
      <span class="chrome__foot"><button class="ch-btn" type="button" id="show-all" (click)="onShowAll()">Show all connections</button></span>
    </aside>

    <div id="atlas" class="chrome">
      <div id="minimap" role="img" [attr.aria-label]="minimapLabel()" tabindex="0">
        <svg viewBox="0 0 208 104" preserveAspectRatio="none" aria-hidden="true">
          <g id="mm-edges">
            @for (e of mmEdges(); track e.key) {
              <line class="mm-edge" [attr.x1]="e.x1" [attr.y1]="e.y1" [attr.x2]="e.x2" [attr.y2]="e.y2" />
            }
          </g>
          <g id="mm-dots">
            @for (d of mmDots(); track d.id) {
              <rect class="mm-dot" width="4" height="4" rx="1" [attr.x]="d.x - 2" [attr.y]="d.y - 2" [attr.data-rank]="d.near ? 'near' : null" [attr.data-moved]="d.moved ? 'true' : null" />
            }
          </g>
          <rect id="mm-view" rx="2" [attr.x]="mmView().x" [attr.y]="mmView().y" [attr.width]="mmView().w" [attr.height]="mmView().h"></rect>
          <circle class="mm-you" id="mm-you" [attr.cx]="mmYou().x" [attr.cy]="mmYou().y" r="6"></circle>
          <circle class="mm-you-core" id="mm-you-core" [attr.cx]="mmYou().x" [attr.cy]="mmYou().y" r="2.2"></circle>
        </svg>
      </div>
      <div id="zoomstack">
        <button class="ch-btn ch-btn--square" type="button" id="zoom-in" title="Zoom in" (click)="store.zoomBy(1.18)">+</button>
        <button class="ch-btn ch-btn--square" type="button" id="recenter" title="Recentre on where I am" (click)="onRecenter()">◎</button>
        <button class="ch-btn ch-btn--square" type="button" id="zoom-out-small" title="Zoom out" (click)="store.zoomBy(0.85)">−</button>
        <button class="ch-btn ch-btn--square" type="button" id="drag-mode" [attr.aria-pressed]="store.dragMode()" title="Arrange mode — reposition the nodes" (click)="toggleArrange()">✥</button>
      </div>
    </div>

    <div id="drag-banner" class="chrome" role="status">
      <span>Arrange mode · drag any card anywhere · nothing else responds until you leave</span>
      <button class="ch-btn" type="button" id="drag-done" style="width:auto" (click)="store.setArrange(false)">Done arranging</button>
    </div>

    <div id="camera" class="chrome">
      <button class="ch-btn" type="button" id="zoom-out" (click)="onFit()">⌕− Zoom out</button>
      <button class="ch-btn" type="button" id="fit" (click)="onFitFocus()">▢ Fit to screen</button>
    </div>

    <p id="gesture" class="chrome">
      Drag to pan · <strong>Alt</strong>+scroll to zoom · scroll to read a node · click a card to travel
      <button class="ch-btn ch-btn--square" type="button" title="Help">?</button>
    </p>

    <div id="probe" class="chrome">
      <span class="chrome__title">Show this screen</span>
      <button class="ch-btn" type="button" id="p-slow" [attr.aria-pressed]="store.probeSlow()" (click)="toggleSlow()">Slow (≥10s)</button>
      <button class="ch-btn" type="button" id="p-offline" [attr.aria-pressed]="store.probeOffline()" (click)="toggleOffline()">Offline</button>
      <button class="ch-btn" type="button" id="p-motion" [attr.aria-pressed]="store.probeReduced()" (click)="toggleReduced()">Reduced motion</button>
    </div>
  `,
})
export class Chrome {
  protected readonly store = inject(WorldStore);
  private readonly auth = inject(AuthService);
  private readonly actions = inject(WorldActionsService);
  /** Named in the topbar so a mock garden is never mistaken for a real one. */
  protected readonly mock = inject(MOCK_MODE, { optional: true });

  protected readonly authed = computed(() => this.auth.isLoggedIn());
  protected readonly accountName = computed(() => this.auth.getCurrentUser()?.firstName ?? 'Account');
  private readonly settings = inject(SettingsStore);
  protected readonly signInUrl = computed(() =>
    classicLoginLink(this.settings.settings().integrations.classicAppUrl || environment.classicAppUrl),
  );

  protected readonly focusName = computed(
    () => this.store.nodes().find(n => n.id === this.store.focusId())?.name ?? '',
  );
  protected readonly focusActions = computed(() =>
    actionsFor(this.store.focusId(), this.store.meta(), this.settings.settings()),
  );
  protected readonly neighbours = computed(() => {
    this.store.focusId();
    return this.store.focusNeighbours();
  });
  protected readonly degreeLabel = computed(() => {
    const n = this.neighbours().length;
    return `${n} vein${n === 1 ? '' : 's'}`;
  });
  protected readonly crumbs = computed(() => {
    const byId = new Map(this.store.nodes().map(n => [n.id, n.name]));
    return this.store.path().map(id => ({ id, name: byId.get(id) ?? id }));
  });

  /** Minimap extent: the lattice ∪ every live position, padded — camera excluded. */
  private readonly mmExtent = computed(() => {
    const t = this.store.targets();
    let minX = 0, minY = 0, maxX = 3600, maxY = 1980;
    for (const id of this.store.order()) {
      const p = t[id];
      minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
      maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
    }
    minX -= 160; minY -= 160; maxX += 160; maxY += 160;
    return { minX, minY, sx: MM_W / (maxX - minX), sy: MM_H / (maxY - minY) };
  });
  private mm(p: { x: number; y: number }): { x: number; y: number } {
    const e = this.mmExtent();
    return { x: (p.x - e.minX) * e.sx, y: (p.y - e.minY) * e.sy };
  }
  protected readonly mmEdges = computed(() => {
    const t = this.store.targets();
    return this.store.edges().map(([a, b]) => {
      const pa = this.mm(t[a]); const pb = this.mm(t[b]);
      return { key: a + '::' + b, x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y };
    });
  });
  protected readonly mmDots = computed(() => {
    const t = this.store.targets();
    return this.store.order().map(id => ({
      id, ...this.mm(t[id]), near: this.store.rankNameOf(id) === 'near', moved: this.store.hasOffset(id),
    }));
  });
  protected readonly mmYou = computed(() => this.mm(this.store.targets()[this.store.focusId()] ?? { x: 0, y: 0 }));
  protected readonly mmView = computed(() => {
    const cam = this.store.camera();
    const e = this.mmExtent();
    const c = this.store.screenCentre();
    const vw = (c.x * 2) / cam.k; const vh = (c.y * 2) / cam.k;
    const wx = -cam.x / cam.k; const wy = -cam.y / cam.k;
    const p = this.mm({ x: wx, y: wy });
    return { x: p.x, y: p.y, w: Math.max(6, vw * e.sx), h: Math.max(4, vh * e.sy) };
  });
  protected readonly minimapLabel = computed(() => {
    const f = this.store.nodes().find(n => n.id === this.store.focusId());
    const deg = this.neighbours().length;
    const path = this.crumbs().map(c => c.name).join(', then ');
    return `World view. You are at ${f?.name}, ${f?.kindLabel}, column ${f?.cell.col} row ${f?.cell.row} of the lattice. ${deg} veins out. Path: ${path}.`;
  });

  constructor() {
    // Probes are body-level material states — rhizome.css keys off these attrs.
    effect(() => {
      document.body.dataset['mode'] = this.store.mode();
      document.body.dataset['drag'] = this.store.dragMode() ? 'on' : '';
      document.body.dataset['speed'] = this.store.probeSlow() ? 'slow' : 'normal';
      document.body.dataset['net'] = this.store.probeOffline() ? 'offline' : 'online';
      if (this.store.probeReduced()) document.body.dataset['motion'] = 'reduced';
      else delete document.body.dataset['motion'];
    });
  }

  protected onSearch(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const q = input.value.trim().toLowerCase();
    if (!q) return;
    const hit = this.store.nodes().find(n => n.name.toLowerCase().includes(q));
    if (!hit) {
      this.store.say(`Nothing named “${input.value}” on the board.`);
      return;
    }
    this.store.say(`Found ${hit.name}.`);
    this.store.go(hit.id);
    input.blur();
  }

  protected openSettings(ev: Event): void {
    ev.stopPropagation(); // the opening click must not reach #shell's click-to-exit
    this.store.mode.set('overview');
  }

  protected goIfThere(id: string): void {
    if (this.store.nodes().some(n => n.id === id)) this.store.go(id);
    else this.store.say('That place is not on this board yet.');
  }

  /** The journal is a live hub; the fixture board reaches the care guide instead. */
  protected goJournal(): void {
    const has = (id: string) => this.store.nodes().some(n => n.id === id);
    this.goIfThere(has('n-journal') ? 'n-journal' : 'n-care');
  }

  protected onBell(): void {
    this.store.say('Travelling to Reminders.');
    this.store.go('n-reminders');
  }

  protected onAction(a: string): void {
    this.actions.dispatch(this.store.focusId(), a);
  }

  protected onShowAll(): void {
    const names = this.neighbours().map(n => n.name).join(', ');
    this.store.say(`Connections from ${this.focusName()}: ${names}.`);
  }

  protected onRecenter(): void {
    this.store.frameFocus(1);
    this.store.say(`Recentred on ${this.focusName()}.`);
  }

  protected toggleArrange(): void {
    const entering = !this.store.dragMode();
    this.store.setArrange(entering);
    if (entering) {
      // anchors-only layout lands first, then bring EVERY node into view
      setTimeout(() => this.store.fitAll(window.innerWidth || 1280, window.innerHeight || 720), 350);
    }
  }

  protected onFitFocus(): void {
    this.store.fitFocusScreen(window.innerHeight || 720);
    this.store.say(`${this.focusName()} fills the screen.`);
  }

  protected onFit(): void {
    this.store.fitAll(window.innerWidth || 1280, window.innerHeight || 720);
    this.store.say(`Whole world in view. You are still at ${this.focusName()}.`);
  }

  protected toggleSlow(): void {
    this.store.probeSlow.update(v => !v);
    this.store.layoutEpoch.update(v => v + 1);
    this.store.say(this.store.probeSlow()
      ? 'Slow network. The geography is already drawn; content arrives into it.'
      : 'Back to normal speed.');
  }
  protected toggleOffline(): void {
    this.store.probeOffline.update(v => !v);
    this.store.say(this.store.probeOffline() ? 'Offline. Changes will be queued.' : 'Back online.');
  }
  protected toggleReduced(): void {
    this.store.probeReduced.update(v => !v);
    this.store.layoutEpoch.update(v => v + 1);
    this.store.say(this.store.probeReduced() ? 'Reduced motion on.' : 'Reduced motion off.');
  }
}
