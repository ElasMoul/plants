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
import { advanceField, cardDrift, driftPhase, Mote, seedField, Size } from '@plantpal/rhizome-engine';
import { DomSanitizer } from '@angular/platform-browser';
import { OVERVIEW_HTML } from '../chrome/overview.html';
import { environment } from '../../environments/environment';
import { StakeForm } from '../forms/stake-form';
import { WorldActionsService } from './world-actions.service';
import { Chrome } from '../chrome/chrome';
import { NodeCard } from '../node/node-card';
import { classicLinkFor, classicLoginLink } from './interop';
import { SettingsStore } from '../settings/settings.store';
import { DeviceStore } from '../settings/device.store';
import { PreferencesClient } from '../settings/preferences.client';
import { PushService } from '../push/push.service';
import { MockBackend } from '../mock/mock-backend';
import { MOCK_MODE } from '../core/mock-mode';
import { SECTION_OF_LABEL } from '../settings/settings.model';
import {
  CARD_DRIFT_HTML,
  MOTION_FOLLOW_HTML,
  OverviewIntent,
  PaneContext,
  coerce,
  renderPane,
  routeOverviewClick,
} from '../settings/settings-panes';
import { ChatStore } from './chat.store';
import type { ChatFailure, ChatTurnDto } from './world.dto';
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
  hint: boolean;
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
  imports: [NodeCard, Chrome, StakeForm],
  template: `
    <canvas id="motes" aria-hidden="true"></canvas>
    <div id="motes-wash" aria-hidden="true"></div>

    <div id="shell" (click)="onShellClick($event)">
      <canvas id="motes-app" aria-hidden="true"></canvas>

      <div id="world-wrap">
        <div
          id="world"
          role="application"
          tabindex="0"
          aria-label="PlantPal botanical network. Arrow keys move along the veins to a neighbour, Enter travels to it, Alt plus the scroll wheel zooms."
          (keydown)="onKeydown($event)"
          (wheel)="onWheel($event)"
          (pointerdown)="onWorldPointerDown($event)"
        >
          <div id="plane" [style.transform]="store.planeTransform()">
            <svg id="veins" viewBox="0 0 3600 1980" aria-hidden="true" preserveAspectRatio="none">
              <g>
                @for (v of veins(); track v.a + '::' + v.b) {
                  <path
                    class="vein"
                    [attr.data-live]="v.live ? true : null"
                    [attr.data-hint]="v.hint ? true : null"
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
                [pending]="store.isPending(n.id)"
                [streamingTurn]="streamFor(n.id)"
                [chatThreadKey]="chatThreadKeyFor(n.id)"
                [chatTurnsKept]="chatTurnsKept()"
                [chatTurns]="chatTurnsFor(n.id)"
                [chatFailure]="chatFailureFor(n.id)"
                [chatExpanded]="chatExpandedFor(n.id)"
                [style.left.px]="store.positionOf(n.id).x"
                [style.top.px]="store.positionOf(n.id).y"
                (click)="onCardClick(n.id, $event)"
                (pointerdown)="onCardPointerDown(n.id, $event)"
                (act)="onAct(n.id, $event)"
                (setMode)="store.setModeFor(n.id, $event)"
              />
            }
          </div>
        </div>
      </div>

      <!-- CHROME: the full furniture ring (H3) -->
      <rz-chrome />
      <p id="live" role="status" aria-live="polite" [attr.data-on]="store.announcement() ? true : null">
        {{ store.announcement() }}
      </p>
    </div>

    <!-- Overview/settings overlay — OUTSIDE #shell so it never scales with it. -->
    <div
      id="overview"
      [innerHTML]="overviewHtml"
      (click)="onOverviewClick($event)"
      (change)="onOverviewChange($event)"
    ></div>

    <rz-stake-form />
  `,
})
export class World {
  protected readonly store = inject(WorldStore);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly graph = inject(WorldGraphService);
  protected readonly actions = inject(WorldActionsService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly settings = inject(SettingsStore);
  private readonly device = inject(DeviceStore);
  private readonly prefs = inject(PreferencesClient);
  private readonly push = inject(PushService);
  private readonly mock = inject(MOCK_MODE, { optional: true });
  private readonly mockBackend = inject(MockBackend);
  private readonly chat = inject(ChatStore);

  /** The companion's live material — read only by n-ask's own card (C15: an
   *  answer arriving dirties one OnPush view and nothing else). */
  private readonly streamingTurn = computed(() => {
    const s = this.chat.streaming();
    return s ? { question: s.question, text: s.text } : null;
  });
  /** The thread the companion is wearing — the one asked in or last failed, not
   *  merely the newest, so an answer never lands on another thread's feed. */
  private readonly askKey = computed(() => this.chat.activeKey());
  private readonly askThread = computed(() => this.chat.thread(this.askKey()) ?? null);

  protected streamFor(id: string): { question: string; text: string } | null {
    return id === 'n-ask' ? this.streamingTurn() : null;
  }

  protected chatTurnsFor(id: string): ChatTurnDto[] {
    return id === 'n-ask' ? (this.askThread()?.turns ?? []) : [];
  }

  protected readonly chatTurnsKept = computed(
    () => this.settings.settings().data.chatTurnsKept,
  );

  protected chatThreadKeyFor(id: string): string | null {
    return id === 'n-ask' ? this.askKey() : null;
  }

  protected chatFailureFor(id: string): ChatFailure | null {
    if (id !== 'n-ask') return null;
    return this.chat.failure(this.askKey()) ?? null;
  }

  protected chatExpandedFor(id: string): boolean {
    return id === 'n-ask' && this.chat.isExpanded(this.askKey());
  }

  protected readonly authed = computed(() => this.auth.isLoggedIn());
  /** Narrow: the refresh only re-arms when the minutes themselves change. */
  private readonly refreshMinutes = computed(
    () => this.settings.settings().general.refreshMinutes,
  );
  /** The reader's own classic base (seeded from the environment) — C: settings are honoured. */
  private readonly classicBase = computed(
    () => this.settings.settings().integrations.classicAppUrl || environment.classicAppUrl,
  );
  protected readonly signInUrl = computed(() => classicLoginLink(this.classicBase()));

  protected readonly focusNode = computed(() =>
    this.store.nodes().find(n => n.id === this.store.focusId()),
  );
  protected readonly focusClassicLink = computed(() => {
    const f = this.focusNode();
    return f ? classicLinkFor(f, this.classicBase()) : null;
  });

  protected readonly veins = computed<VeinLine[]>(() => {
    const focus = this.store.focusId();
    const hint = this.store.hintedVein();
    const pos = this.store.targets();
    const unknownIds = new Set(this.store.nodes().filter(n => n.unknown).map(n => n.id));
    return this.store.edges().map(([a, b]) => {
      const pa = pos[a] ?? { x: 0, y: 0 };
      const pb = pos[b] ?? { x: 0, y: 0 };
      return {
        a, b,
        x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y,
        live: a === focus || b === focus,
        hint: !!hint && ((hint[0] === a && hint[1] === b) || (hint[0] === b && hint[1] === a)),
        unknown: unknownIds.has(a) || unknownIds.has(b),
      };
    });
  });

  /** The mode the overlay effect last acted on — a transition, not a value. */
  private lastMode: 'app' | 'overview' = 'app';

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
    // Focus leaving the companion (and the plant its thread belongs to) stops the
    // answer being written — the partial is kept as a turn, never silently dropped.
    effect(() => this.actions.noteFocus(this.store.focusId()));
    // A source or scenario switch, and going offline, stop it for the same reason.
    effect(() => {
      if (this.store.probeOffline()) this.actions.cancelAsk();
    });
    // A successful mutation re-assembles the world (a new node takes a free cell).
    effect(() => {
      if (this.actions.reloadRequested() > 0) this.loadLive();
    });
    // Cancel must be able to put back exactly what was here when the overlay
    // opened — whatever route opened it (the gear, or the account's own stake).
    effect(() => {
      const mode = this.store.mode();
      if (mode === this.lastMode) return;
      const was = this.lastMode;
      this.lastMode = mode;
      if (mode === 'overview') {
        this.settings.open();
        // The five server-backed keys are PlantPal's, not this device's: ask for
        // them as the overlay opens, so no pane has to open on its failure state.
        this.ensureServerPrefs();
      } else if (was === 'overview') this.settings.save();
    });
    // The pane follows its section, the settings, and what PlantPal said about models.
    effect(() => {
      this.settings.section();
      this.settings.settings();
      this.settings.serverPrefs();
      this.settings.prefsState();
      queueMicrotask(() => this.renderSettingsPane());
    });
    // The periodic refresh follows its setting, and is re-armed when it changes.
    effect(() => {
      this.scheduleRefresh(this.refreshMinutes());
    });
    this.destroyRef.onDestroy(() => {
      if (this.pollTimer) clearTimeout(this.pollTimer);
      if (this.refreshTimer) clearInterval(this.refreshTimer);
    });
    afterNextRender(() => {
      this.syncCentre();
      this.measureAndSettle();
      this.startMotes();
      this.startDrift();
      this.renderSettingsPane();
      this.labelCloseSettings();
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

  /**
   * freeCentre(), faithfully: the focus lands in the centre of the box left
   * between the rails and bars, measured live — falling back to the full
   * viewport when the residue is under 240×200 (small viewports).
   */
  private syncCentre(): void {
    const w = window.innerWidth || 1280;
    const h = window.innerHeight || 720;
    let l = 0, r = w, t = 0, b = h;
    const box = (sel: string) => document.querySelector(sel)?.getBoundingClientRect();
    const act = box('#actions'); const nav = box('#navto');
    const top = box('#topbar'); const cam = box('#camera');
    if (act?.width) l = Math.max(l, act.right);
    if (nav?.width) r = Math.min(r, nav.left);
    if (top?.height) t = Math.max(t, top.bottom);
    if (cam?.height) b = Math.min(b, cam.top);
    if (r - l < 240 || b - t < 200) { l = 0; r = w; t = 0; b = h; }
    this.store.setScreenCentre({ x: (l + r) / 2, y: (t + b) / 2 });
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
    if (this.store.dragMode()) return; // arrange: cards are drag surfaces only
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
      const label = stake.textContent?.trim() ?? 'Action';
      if (stake.getAttribute('aria-disabled') === 'true') {
        // a disabled stake still answers — in words, with the reason it is not possible
        const reason = stake.dataset['reason'] ?? 'Nothing is due';
        const clause = (reason[0]?.toLowerCase() ?? '') + reason.slice(1).replace(/\.$/, '');
        this.store.say(`${label} is not possible right now — ${clause}.`);
        return;
      }
      this.actions.dispatch(id, label, stake.dataset['arg']);
      return;
    }
    if (t.closest('.n__modes, .n__grip, a, button')) return;
    this.store.go(id);
  }

  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private firstLoad = true;

  private loadLive(): void {
    if (!this.authed()) return;
    if (this.firstLoad) this.store.markLoading();
    this.graph
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => {
          if (this.firstLoad) {
            this.store.setWorld(data);
            this.firstLoad = false;
          } else {
            this.store.updateWorld(data); // arrivals never move the camera (C9)
          }
          // the async families: poll while a scan or a disease description is in flight
          if (this.pollTimer) clearTimeout(this.pollTimer);
          if (data.hasPendingScan || this.store.hasPendingDescription()) {
            const every = this.settings.settings().general.pollIntervalMs;
            this.pollTimer = setTimeout(() => this.loadLive(), every);
          }
        },
        error: () => this.store.markError(),
      });
  }

  /** A quiet periodic refresh: care logged on the phone lands here without a hop, and
   *  an arrival never moves the camera (C9). Zero minutes turns it off. */
  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  private scheduleRefresh(minutes: number): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    this.refreshTimer = null;
    if (!this.authed() || minutes <= 0) return;
    this.refreshTimer = setInterval(() => this.loadLive(), minutes * 60_000);
  }

  protected onAct(nodeId: string, way: string): void {
    this.actions.dispatch(nodeId, way);
  }

  // ── H4: overview/settings ────────────────────────────────────────────────
  protected readonly overviewHtml = inject(DomSanitizer).bypassSecurityTrustHtml(OVERVIEW_HTML);

  /** Any click on the shrunken app card returns to the app (prototype law). */
  protected onShellClick(ev: Event): void {
    if (this.store.mode() === 'overview') {
      ev.stopPropagation();
      // leaving keeps what is on screen — the same as Save, and the effect above
      // clears the snapshot so a later Cancel can never reach back past here.
      this.settings.save();
      this.store.persistLayout();
      this.store.mode.set('app');
    }
  }

  /** Delegated wiring for the verbatim overlay markup — one pure classifier, one switch. */
  protected onOverviewClick(ev: Event): void {
    const intent = routeOverviewClick(ev.target as HTMLElement);
    if (!intent) return;
    ev.stopPropagation();
    this.applyIntent(intent);
  }

  /** The free-text fields, and anything a keyboard changes rather than clicks. */
  protected onOverviewChange(ev: Event): void {
    const el = ev.target as HTMLInputElement | HTMLSelectElement;
    const key = el.dataset['set'];
    if (!key) return;
    this.applyIntent({ kind: 'set', key, value: coerce(el.value, el.dataset['kind']) });
  }

  private applyIntent(intent: OverviewIntent): void {
    switch (intent.kind) {
      case 'section':
        this.settings.section.set(intent.section);
        return;
      case 'set':
        this.applyKey(intent.key, intent.value);
        return;
      case 'action':
        this.runAction(intent.name);
        return;
      case 'ui':
        // setUI brings the interface's own default palette with it — order matters.
        this.store.setUI(intent.value);
        this.settings.patch({
          'appearance.ui': intent.value,
          'appearance.palette': this.store.palette(),
        });
        this.refreshPickers();
        return;
      case 'palette':
        this.store.setPalette(intent.value);
        this.settings.set('appearance.palette', intent.value);
        this.refreshPickers();
        return;
      case 'save':
      case 'close':
        this.settings.save();
        // "Remember the layout" is only acted on here, so Cancel can put it back.
        this.store.persistLayout();
        this.store.mode.set('app');
        return;
      case 'cancel':
        this.settings.cancel();
        this.reapplyAppearance();
        this.actions.reloadRequested.update(v => v + 1);
        this.store.mode.set('app');
        return;
      case 'reset':
        this.settings.reset();
        this.reapplyAppearance();
        this.actions.reloadRequested.update(v => v + 1);
        this.store.say('Defaults restored. Your PlantPal preferences on the server are unchanged.');
        return;
    }
  }

  /** The five server-backed keys travel to PlantPal at once; the rest are this device's. */
  private static readonly SERVER_KEYS: Record<string, string> = {
    'ai.visionModelPreference': 'Vision model',
    'ai.reasoningModelPreference': 'Reasoning model',
    'ai.plantnetProject': 'PlantNet flora',
    'ai.plantnetLang': 'PlantNet language',
    'profile.businessTier': 'Garden type',
  };

  private applyKey(key: string, value: unknown): void {
    const label = World.SERVER_KEYS[key];
    if (label) {
      const field = key.split('.')[1];
      const current = this.settings.serverPrefs();
      const body: Record<string, unknown> = { [field]: value };
      // the classic app sends both model choices together; keep that habit
      if (field === 'visionModelPreference' && current) {
        body['reasoningModelPreference'] = current.reasoningModelPreference;
      }
      if (field === 'reasoningModelPreference' && current) {
        body['visionModelPreference'] = current.visionModelPreference;
      }
      this.prefs.update(body).subscribe({
        next: () => this.store.say(`${label} saved to PlantPal.`),
        error: () =>
          this.store.say(`PlantPal did not take the ${label.toLowerCase()}. Nothing changed.`),
      });
      return;
    }
    this.settings.set(key, value);
    this.applySetting(key);
  }

  /** Keys the assembly reads are re-assembled; the timed ones re-arm themselves. */
  private static readonly REASSEMBLE = new Set([
    'general.keepFinished', 'general.dateStyle', 'data.stepReminders', 'reminders.snooze',
    'treatment.pause', 'data.careLogPageSize', 'data.pageSize', 'notifications.dueWindow',
    'integrations.showApiIds', 'profile.displayName', 'profile.units', 'profile.quietHours',
    'integrations.openInClassic',
  ]);

  private applySetting(key: string): void {
    if (key === 'privacy.rememberLayout') {
      // Turning it ON writes at once; turning it OFF waits for Save, so Cancel
      // can restore the setting AND the geography it was about to discard.
      if (this.settings.settings().privacy.rememberLayout) this.store.persistLayout();
      return;
    }
    if (key === 'notifications.push') {
      const on = this.settings.settings().notifications.push === 'on';
      (on ? this.push.enable() : this.push.disable()).subscribe();
      return;
    }
    // general.pollIntervalMs is read at use; general.refreshMinutes re-arms in an effect
    if (World.REASSEMBLE.has(key)) this.actions.reloadRequested.update(v => v + 1);
  }

  private runAction(name: string): void {
    switch (name) {
      case 'reload':
        this.settings.save();
        location.reload();
        return;
      case 'reset-mock':
        this.mockBackend.reset(this.settings.settings().data.mockScenario, Date.now());
        this.actions.reloadRequested.update(v => v + 1);
        this.store.say('The mock garden is back at its seed.');
        return;
      case 'mock-fail-next':
        this.mockBackend.failNext = true;
        this.store.say('The next change will be refused, once.');
        return;
      case 'forget-device':
        this.settings.reset();
        this.device.clear();
        this.store.forgetLayout();
        this.reapplyAppearance();
        this.actions.reloadRequested.update(v => v + 1);
        this.store.say('This device keeps nothing about you now. Your PlantPal garden is untouched.');
        return;
      case 'forget-push':
        this.push.disable().subscribe();
        return;
      case 'sign-out':
        this.actions.dispatch('n-account', 'Sign out here');
        return;
      case 'save-plantnet': {
        const field = document.querySelector<HTMLInputElement>(
          '#settings .pane input[data-set="ai.plantnetProject"]',
        );
        if (!field) {
          this.store.say('The flora field is not on screen — nothing was sent to PlantPal.');
          return;
        }
        const lang = this.settings.serverPrefs()?.plantnetLang ?? 'en';
        this.prefs.update({ plantnetProject: field.value, plantnetLang: lang }).subscribe({
          next: () => this.store.say('PlantNet preferences saved to PlantPal.'),
          error: () =>
            this.store.say('PlantPal did not take the PlantNet preferences. Nothing changed.'),
        });
        return;
      }
      case 'reload-prefs':
        this.prefs.read().subscribe({ error: () => undefined });
        return;
      default:
        this.store.say(`“${name}” is not something PlantPal can do from here yet.`);
    }
  }

  /** Put the chosen reading back on the document after Cancel or Reset. */
  private reapplyAppearance(): void {
    const look = this.settings.settings().appearance;
    this.store.setUI(look.ui);
    this.store.setPalette(look.palette);
    this.refreshPickers();
  }

  /** The pinned Appearance pane, captured once and re-inserted rather than rebuilt. */
  private capturedAppearance: string | null = null;

  /** A rewrite of the pane must not move the reader: the scroll offset and the
   *  control they just pressed are put back where they were (C: a mutation never
   *  moves focus). Controls are identified by what they do, not by index. */
  private paneMark(pane: HTMLElement): { top: number; sel: string | null } {
    const active = document.activeElement as HTMLElement | null;
    let sel: string | null = null;
    if (active && pane.contains(active)) {
      const set = active.dataset['set'];
      const value = active.dataset['value'];
      const act = active.dataset['action'];
      if (set !== undefined && value !== undefined) {
        sel = `[data-set="${CSS.escape(set)}"][data-value="${CSS.escape(value)}"]`;
      } else if (set !== undefined) {
        sel = `[data-set="${CSS.escape(set)}"]`;
      } else if (act !== undefined) {
        sel = `[data-action="${CSS.escape(act)}"]`;
      }
    }
    return { top: pane.scrollTop, sel };
  }

  private paneRestore(pane: HTMLElement, mark: { top: number; sel: string | null }): void {
    pane.scrollTop = mark.top;
    if (!mark.sel) return;
    const again = pane.querySelector<HTMLElement>(mark.sel);
    if (again) again.focus({ preventScroll: true });
    pane.scrollTop = mark.top;
  }

  /** Read PlantPal's own preferences once per overlay opening; a read already in
   *  flight, or an answer already held, is left alone. */
  private ensureServerPrefs(): void {
    if (this.settings.prefsState() === 'reading') return;
    if (this.settings.serverPrefs() && this.settings.prefsState() === 'idle') return;
    this.prefs.read().subscribe({ error: () => undefined });
  }

  /** The pin's ✕ commits, exactly as Save does. The pin is never hand-edited, so
   *  the button is told what it means here instead of carrying a stale title. */
  private labelCloseSettings(): void {
    const btn = document.querySelector<HTMLElement>('#close-settings');
    if (!btn) return;
    btn.setAttribute('title', 'Close settings and keep these changes');
    btn.setAttribute('aria-label', 'Close settings and keep these changes');
  }

  private renderSettingsPane(): void {
    const pane = document.querySelector<HTMLElement>('#settings .pane');
    if (!pane) return;
    const mark = this.paneMark(pane);
    if (this.capturedAppearance === null) this.capturedAppearance = pane.innerHTML;
    const section = this.settings.section();
    for (const b of Array.from(document.querySelectorAll<HTMLElement>('#settings nav button'))) {
      const mine = SECTION_OF_LABEL[(b.textContent ?? '').trim()];
      if (mine === section) b.setAttribute('aria-current', 'true');
      else b.removeAttribute('aria-current');
    }
    const html = renderPane(section, this.paneContext());
    if (html === null) {
      // Appearance is the pin's own markup: re-inserted, never rebuilt. Only the
      // motion value becomes a control, and the drift choice joins it beneath.
      pane.innerHTML = this.capturedAppearance;
      const s = this.settings.settings();
      const dl = pane.querySelector('dl.rows');
      const dd = dl?.querySelector('.row:last-child dd');
      if (dd) dd.innerHTML = MOTION_FOLLOW_HTML(s);
      dl?.insertAdjacentHTML('afterend', CARD_DRIFT_HTML(s));
      this.refreshPickers();
      this.paneRestore(pane, mark);
      return;
    }
    pane.innerHTML = html;
    this.paneRestore(pane, mark);
  }

  private paneContext(): PaneContext {
    const user = this.auth.getCurrentUser();
    const times = this.store.lastSources()?.sessionTimes;
    const name = user ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() : '';
    return {
      settings: this.settings.settings(),
      prefs: this.settings.serverPrefs(),
      prefsState: this.settings.prefsState(),
      mock: !!this.mock?.enabled,
      push: this.push.state(),
      pushEndpoint: this.device.state().push?.endpoint,
      pushSubscribedAt: this.device.state().push?.subscribedAt,
      account: {
        name: name || 'not signed in',
        email: user?.email ?? 'not signed in',
        session: this.mock?.enabled
          ? 'the mock garden — no real session'
          : (times?.expiresAt ?? 'this browser'),
      },
      vapidConfigured: !!environment.vapidPublicKey,
    };
  }

  /** aria-pressed + data-for-ui visibility on the verbatim pickers. */
  private refreshPickers(): void {
    const ui = this.store.ui();
    const palette = this.store.palette();
    for (const b of Array.from(document.querySelectorAll<HTMLElement>('#interfaces .palette'))) {
      b.setAttribute('aria-pressed', String(b.dataset['ui'] === ui));
    }
    for (const b of Array.from(document.querySelectorAll<HTMLElement>('#palettes .palette'))) {
      const forUi = b.dataset['forUi'];
      b.hidden = forUi !== ui;
      b.setAttribute('aria-pressed', String(b.dataset['palette'] === palette));
    }
  }

  // ── H4: keyboard navigation on #world ────────────────────────────────────
  private cursorIdx = -1;

  protected onKeydown(ev: KeyboardEvent): void {
    const key = ev.key;
    if (key === 'Escape') {
      ev.preventDefault();
      if (this.store.dragMode()) this.store.setArrange(false);
      else this.store.goBack();
      return;
    }
    if (this.store.dragMode()) return;
    const neighbours = this.store.focusNeighbours();
    if (!neighbours.length) return;

    if (key === 'Enter' || key === ' ') {
      const hint = this.store.hintedVein();
      if (hint) {
        ev.preventDefault();
        this.store.go(hint[1] === this.store.focusId() ? hint[0] : hint[1]);
        this.cursorIdx = -1;
      }
      return;
    }
    if (key === 'Tab' && !ev.shiftKey) {
      ev.preventDefault();
      this.cursorIdx = (this.cursorIdx + 1) % neighbours.length;
      const n = neighbours[this.cursorIdx];
      this.store.hintedVein.set([this.store.focusId(), n.id]);
      this.store.say(`${n.name}, ${this.cursorIdx + 1} of ${neighbours.length}. Press Enter to travel.`);
      return;
    }
    const dirs: Record<string, [number, number]> = {
      ArrowRight: [1, 0], ArrowLeft: [-1, 0], ArrowDown: [0, 1], ArrowUp: [0, -1],
    };
    const dir = dirs[key];
    if (!dir) return;
    ev.preventDefault();
    const t = this.store.targets();
    const f = t[this.store.focusId()];
    // candidates at least 40px along the axis, nearest first (theme-a)
    const scored = neighbours
      .map(n => {
        const p = t[n.id];
        const along = (p.x - f.x) * dir[0] + (p.y - f.y) * dir[1];
        const dist = Math.hypot(p.x - f.x, p.y - f.y);
        return { n, along, dist };
      })
      .filter(c => c.along > 40)
      .sort((a, b) => a.dist - b.dist);
    if (!scored.length) {
      this.store.say('No vein that way.');
      return;
    }
    const pick = scored[0].n;
    this.store.hintedVein.set([this.store.focusId(), pick.id]);
    this.store.say(`${pick.name} — press Enter to travel.`);
  }

  // ── H4: Alt+wheel zoom (pointer-anchored) + drag-pan ─────────────────────
  protected onWheel(ev: WheelEvent): void {
    if (!ev.altKey || this.store.dragMode()) return; // bare wheel scrolls the focus body
    ev.preventDefault();
    const cam = this.store.camera();
    const factor = ev.deltaY < 0 ? 1.12 : 0.89;
    const k = Math.min(1.9, Math.max(0.28, cam.k * factor));
    const px = ev.clientX;
    const py = ev.clientY;
    this.store.camera.set({
      x: px - ((px - cam.x) * k) / cam.k,
      y: py - ((py - cam.y) * k) / cam.k,
      k,
    });
  }

  protected onWorldPointerDown(ev: PointerEvent): void {
    const t = ev.target as HTMLElement;
    if (t.closest('rz-node') || t.classList.contains('vein-hit')) return;
    if (this.store.dragMode()) return;
    const worldEl = t.closest('#world') as HTMLElement;
    worldEl.setPointerCapture(ev.pointerId);
    worldEl.dataset['panning'] = 'true';
    const start = this.store.camera();
    const sx = ev.clientX;
    const sy = ev.clientY;
    const move = (e: PointerEvent) => {
      this.store.camera.set({ x: start.x + e.clientX - sx, y: start.y + e.clientY - sy, k: start.k });
    };
    const up = () => {
      delete worldEl.dataset['panning'];
      worldEl.removeEventListener('pointermove', move);
      worldEl.removeEventListener('pointerup', up);
    };
    worldEl.addEventListener('pointermove', move);
    worldEl.addEventListener('pointerup', up);
  }

  // ── H4: arrange-mode card dragging (÷ cam.k; offsets persist) ────────────
  protected onCardPointerDown(id: string, ev: PointerEvent): void {
    const isGrip = (ev.target as HTMLElement).closest('.n__grip');
    if (!this.store.dragMode() && !isGrip) return;
    ev.preventDefault();
    ev.stopPropagation();
    const el = (ev.target as HTMLElement).closest('rz-node') as HTMLElement;
    el.setPointerCapture(ev.pointerId);
    el.dataset['dragging'] = 'true';
    const cam = this.store.camera();
    const node = this.store.nodes().find(n => n.id === id);
    const start = this.store.offsets()[id] ?? node?.offset ?? { x: 0, y: 0 };
    const sx = ev.clientX;
    const sy = ev.clientY;
    const move = (e: PointerEvent) => {
      this.store.setOffset(id, {
        x: start.x + (e.clientX - sx) / cam.k,
        y: start.y + (e.clientY - sy) / cam.k,
      });
    };
    const up = () => {
      delete el.dataset['dragging'];
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up);
      this.store.say(`${node?.name} moved. Its position is kept.`);
    };
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up);
  }

  // ── H4: card drift (~30fps, render offset only — never layout) ───────────
  private driftRaf = 0;
  private driftTick = 0;

  private startDrift(): void {
    const frame = (now: number): void => {
      this.driftRaf = requestAnimationFrame(frame);
      if (++this.driftTick % 2) return;
      if (this.store.probeReduced() || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      const still =
        this.store.mode() === 'overview' ||
        this.store.dragMode() ||
        !this.settings.settings().appearance.cardDrift;
      const focus = this.store.focusId();
      const els = (this.host.nativeElement as HTMLElement).querySelectorAll('rz-node');
      let i = 0;
      for (const el of Array.from(els) as HTMLElement[]) {
        if (still || el.id === focus) {
          el.style.setProperty('--jx', '0px');
          el.style.setProperty('--jy', '0px');
        } else {
          const { jx, jy } = cardDrift(driftPhase(i), now);
          el.style.setProperty('--jx', jx.toFixed(2) + 'px');
          el.style.setProperty('--jy', jy.toFixed(2) + 'px');
        }
        i++;
      }
    };
    this.driftRaf = requestAnimationFrame(frame);
    this.destroyRef.onDestroy(() => cancelAnimationFrame(this.driftRaf));
  }
}
