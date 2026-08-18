import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RankName } from '@plantpal/rhizome-engine';
import { NodeState, WorldNode } from '../world/world.model';

/**
 * A single node card (rz-node). Scale carries rank (C12): four widths keyed off
 * data-rank in CSS. The signature tick (::before) carries kind by colour and rank
 * by length, so a card answers "how far / what kind" even with the text blurred
 * (C3). The recap sheds its second line at the fringe.
 */
@Component({
  selector: 'rz-node',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'n',
    '[attr.data-rank]': 'rank()',
    '[attr.data-kindkey]': 'node().kind',
    '[attr.data-focus]': 'focus()',
    '[attr.data-state]': 'state()',
    '[attr.data-unknown]': 'dashed() ? true : null',
    '[attr.aria-current]': 'focus() ? "true" : "false"',
    role: 'group',
    '[attr.aria-label]': 'ariaLabel()',
  },
  template: `
    @switch (state()) {
      @case ('loading') {
        <!-- Skeleton: the same block shapes the real content will take, so nothing
             reflows when it lands (C22). No spinner, never the word "loading". -->
        <div class="n__skel n__skel--kind"></div>
        <div class="n__skel n__skel--name"></div>
        <div class="n__skel n__skel--line"></div>
      }
      @case ('failed') {
        <div class="n__head">
          <span class="n__kind">{{ node().kindLabel }}</span>
          <h2 class="n__name">{{ node().name }}</h2>
        </div>
        <div class="n__fail">
          <p class="n__fail-fact">{{ node().failure?.fact }}</p>
          @if (node().failure?.time) { <p class="n__fail-time">{{ node().failure?.time }}</p> }
          @if (node().failure?.dataNote) { <p class="n__fail-data">{{ node().failure?.dataNote }}</p> }
          <div class="n__ways">
            @for (way of node().failure?.waysForward ?? []; track way) {
              <button type="button" class="n__way" (click)="act.emit(way); $event.stopPropagation()">{{ way }}</button>
            }
          </div>
        </div>
      }
      @default {
        <div class="n__head">
          <span class="n__kind">{{ node().kindLabel }}</span>
          <h2 class="n__name">{{ node().name }}</h2>
        </div>
        <div class="n__recap">
          <p class="n__recap-line">{{ node().recap }}</p>
          @if (rank() !== 'fringe' && node().recapNote) {
            <p class="n__recap-note">{{ node().recapNote }}</p>
          }
        </div>
        @if (state() === 'empty') { <p class="n__afford">Nothing here yet — begin</p> }
        @if (state() === 'unknown') { <p class="n__afford">Not fetched — open to load</p> }
        @if (focus() && node().detail?.length) {
          <div class="n__detail">
            @for (line of node().detail ?? []; track line) { <p>{{ line }}</p> }
          </div>
        }
      }
    }
  `,
  styles: [
    `
      :host {
        --kind: var(--vs-ink-muted);
        position: absolute;
        display: block;
        width: var(--vs-card-fringe, 180px);
        /* centre the card on its world point */
        transform: translate(-50%, -50%);
        padding: var(--vs-gutter-card, 1rem);
        background: var(--vs-sheet-quiet, #12100e);
        color: var(--vs-ink);
        border: var(--vs-hair, 1px) solid var(--vs-membrane, rgba(255, 255, 255, 0.08));
        border-radius: var(--vs-corner-card, 5px);
        box-shadow: var(--vs-halo-quiet, 0 6px 24px rgba(0,0,0,0.45));
        cursor: pointer;
        transition:
          width 300ms ease,
          opacity 300ms ease;
        will-change: transform;
      }

      /* signature tick: colour = kind, length = rank */
      :host::before {
        content: '';
        position: absolute;
        left: var(--vs-gutter-card, 1rem);
        top: calc(var(--vs-gutter-card, 1rem) - 6px);
        height: 3px;
        width: 22px;
        background: var(--kind);
        border-radius: 2px;
      }

      :host[data-kindkey='collection'] { --kind: var(--vs-kind-collection); }
      :host[data-kindkey='species'] { --kind: var(--vs-kind-species); }
      :host[data-kindkey='plant'] { --kind: var(--vs-kind-plant); }
      :host[data-kindkey='guide'] { --kind: var(--vs-kind-guide); }
      :host[data-kindkey='problem'] { --kind: var(--vs-kind-problem); }
      :host[data-kindkey='journal'] { --kind: var(--vs-kind-journal); }
      :host[data-kindkey='platform'] { --kind: var(--vs-kind-platform); }
      :host[data-kindkey='region'] { --kind: var(--vs-kind-region); }

      /* four ranks, four widths + inks (C12) */
      :host[data-rank='fringe'] { width: var(--vs-card-fringe, 180px); opacity: 0.62; }
      :host[data-rank='far'] { width: var(--vs-card-far, 240px); opacity: 0.82; }
      :host[data-rank='near'] { width: var(--vs-card-near, 300px); opacity: 1; }
      :host[data-rank='focus'] {
        width: var(--vs-card-focus, 436px);
        opacity: 1;
        border-color: var(--kind);
        box-shadow: var(--vs-halo-lit, 0 10px 40px rgba(0,0,0,0.6));
      }

      /* unknown / unfetched region — dashed, still traversable (C22-C25) */
      :host[data-unknown] {
        border-style: dashed;
        background: transparent;
      }

      .n__head { margin-top: 4px; }
      .n__kind {
        font-family: var(--vs-chrome-face, monospace);
        font-size: var(--vs-rung-18, 0.7rem);
        letter-spacing: 0.12em;
        text-transform: uppercase;
        color: var(--vs-ink-faint);
      }
      .n__name {
        margin: 2px 0 0;
        font-family: var(--vs-face-name, serif);
        font-size: var(--vs-rung-25, 1.1rem);
        font-weight: 600;
        line-height: 1.15;
        color: var(--vs-ink);
      }
      .n__recap { margin-top: 8px; }
      .n__recap-line {
        margin: 0;
        font-size: var(--vs-rung-25, 0.85rem);
        color: var(--vs-ink-second);
      }
      .n__recap-note {
        margin: 2px 0 0;
        font-size: var(--vs-rung-18, 0.72rem);
        color: var(--vs-ink-faint);
      }
      :host[data-rank='focus'] .n__name { font-size: var(--vs-rung-55, 1.6rem); }

      /* dashed for empty/unknown (unknown data-attr set by dashed()) */
      :host[data-state='empty'],
      :host[data-state='unknown'] {
        border-style: dashed;
        background: transparent;
      }
      .n__afford {
        margin: 8px 0 0;
        font-family: var(--vs-chrome-face, monospace);
        font-size: var(--vs-rung-18, 0.72rem);
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--vs-ink-faint);
      }

      /* skeleton blocks — same footprint as the real content (C22) */
      .n__skel {
        background: var(--vs-membrane, rgba(255, 255, 255, 0.08));
        border-radius: 3px;
        animation: rz-pulse 1.4s ease-in-out infinite;
      }
      .n__skel--kind { width: 40%; height: 10px; margin-top: 6px; }
      .n__skel--name { width: 75%; height: 18px; margin-top: 10px; }
      .n__skel--line { width: 60%; height: 12px; margin-top: 12px; }
      @keyframes rz-pulse {
        0%, 100% { opacity: 0.4; }
        50% { opacity: 0.8; }
      }
      @media (prefers-reduced-motion: reduce) {
        .n__skel { animation: none; }
      }

      /* failure — named fact, time, and two ways forward, camera undisturbed (C25) */
      :host[data-state='failed'] { border-color: var(--vs-kind-problem); }
      .n__fail { margin-top: 8px; }
      .n__fail-fact { margin: 0; font-size: var(--vs-rung-25, 0.85rem); color: var(--vs-ink); }
      .n__fail-time,
      .n__fail-data { margin: 2px 0 0; font-size: var(--vs-rung-18, 0.72rem); color: var(--vs-ink-faint); }
      .n__ways { display: flex; gap: 6px; margin-top: 10px; }
      .n__way {
        padding: 4px 10px;
        font-family: var(--vs-chrome-face, monospace);
        font-size: var(--vs-rung-18, 0.72rem);
        text-transform: uppercase;
        letter-spacing: 0.06em;
        background: transparent;
        color: var(--vs-ink);
        border: var(--vs-hair, 1px) solid var(--vs-membrane, rgba(255, 255, 255, 0.14));
        border-radius: var(--vs-corner-card, 5px);
        cursor: pointer;
      }
      .n__way:hover { border-color: var(--vs-ink-muted); }

      .n__detail {
        margin-top: 10px;
        padding-top: 8px;
        border-top: var(--vs-hair, 1px) solid var(--vs-membrane-rule, rgba(255, 255, 255, 0.06));
      }
      .n__detail p { margin: 0 0 4px; font-size: var(--vs-rung-25, 0.85rem); color: var(--vs-ink-second); }
    `,
  ],
})
export class NodeCard {
  readonly node = input.required<WorldNode>();
  readonly rank = input.required<RankName>();
  readonly focus = input<boolean>(false);

  /** Emitted when a failure card's "way forward" button is pressed. */
  readonly act = output<string>();

  readonly state = computed<NodeState>(() => this.node().state ?? 'ready');
  readonly dashed = computed(() => this.node().unknown || this.state() === 'empty' || this.state() === 'unknown');

  readonly ariaLabel = computed(() => {
    const n = this.node();
    const s = this.state();
    const status = s === 'ready' ? '' : ` · ${s}`;
    return `${n.kindLabel} · ${n.name} · ${n.recap}${status}${this.focus() ? ' · you are here' : ''}`;
  });
}
