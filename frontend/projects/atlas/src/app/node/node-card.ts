import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RankName } from '@plantpal/rhizome-engine';
import { NodeState, WorldNode } from '../world/world.model';
import { NODE_BODIES } from '../world/world.bodies';

/** A card's size pin: MIN (summary), AUTO (follow focus), FULL (pinned open). */
export type NodeMode = 'min' | 'auto' | 'full';

/**
 * A node card. The host IS the prototype's `article.n`; the body is the
 * prototype's own .n__body HTML rendered verbatim (world.bodies.ts) so every
 * plate, section, stake, hop, state panel, feed, staleness line and skeleton
 * shape is byte-identical to the round-9 artifact. Live-assembled nodes without
 * a prototype body fall back to a structural rendering. rhizome.css styles both.
 */
@Component({
  selector: 'rz-node',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'n',
    role: 'group',
    tabindex: '-1',
    '[attr.data-cell]': 'node().cell.col + "," + node().cell.row',
    '[attr.data-kind]': 'node().kindLabel',
    '[attr.data-kindkey]': 'node().kind',
    '[attr.data-name]': 'node().name',
    '[attr.data-recap]': 'node().recap',
    '[attr.data-rank]': 'rank()',
    '[attr.data-focus]': 'focus()',
    '[attr.data-show]': 'show()',
    '[attr.data-unknown]': 'node().unknown ? true : null',
    '[attr.data-pending]': 'node().state === "loading" ? true : null',
    '[attr.aria-current]': 'focus() ? "true" : "false"',
    '[attr.aria-label]': 'ariaLabel()',
  },
  template: `
    <div class="n__head">
      <span class="n__thumb" aria-hidden="true">{{ node().glyph }}</span>
      <div><span class="n__kind">{{ node().kindLabel }}</span><h2 class="n__name">{{ node().name }}</h2></div>
      <div class="n__tools">
        @if (focus()) {
          <div class="n__modes" role="group" aria-label="Card size">
            <button type="button" title="Pin to summary" [attr.aria-pressed]="mode() === 'min'"
                    (click)="setMode.emit('min'); $event.stopPropagation()">MIN</button>
            <button type="button" title="Follow focus" [attr.aria-pressed]="mode() === 'auto'"
                    (click)="setMode.emit('auto'); $event.stopPropagation()">AUTO</button>
            <button type="button" title="Pin to full content" [attr.aria-pressed]="mode() === 'full'"
                    (click)="setMode.emit('full'); $event.stopPropagation()">FULL</button>
          </div>
          <button type="button" class="n__grip" title="Drag this node"><span aria-hidden="true">✥</span></button>
        }
      </div>
    </div>
    @if (bodyHtml(); as body) {
      <div class="n__body" [innerHTML]="body"></div>
    } @else {
      <div class="n__body">
        <div class="n__recap">
          <p class="n__recap-line">{{ node().recap }}</p>
          @if (node().recapNote) { <p class="n__recap-note">{{ node().recapNote }}</p> }
        </div>
        <div class="n__skel" aria-hidden="true">
          <div class="sk sk--name"></div><div class="sk sk--line"></div><div class="sk sk--line is-short"></div>
        </div>
        <div class="n__full">
          @switch (state()) {
            @case ('failed') {
              <div class="state state--error">
                <div class="state__head"><h4 class="state__title">{{ node().failure?.fact }}</h4></div>
                @if (node().failure?.time) { <p class="state__note">{{ node().failure?.time }}</p> }
                @if (node().failure?.dataNote) { <p class="state__note">{{ node().failure?.dataNote }}</p> }
                <div class="btn-row">
                  @for (way of node().failure?.waysForward ?? []; track way) {
                    <button class="stake" type="button" (click)="act.emit(way); $event.stopPropagation()">{{ way }}</button>
                  }
                </div>
              </div>
            }
            @case ('empty') {
              <div class="state state--empty">
                <div class="empty-plot"><span aria-hidden="true">◌</span></div>
                <p class="state__note">Nothing here yet.</p>
              </div>
            }
            @case ('unknown') {
              <div class="state state--unknown">
                <div class="state__head"><h4 class="state__title">Not fetched yet</h4><span class="state__id">region · unknown</span></div>
                <button class="stake stake--quiet" type="button" (click)="act.emit('Fetch this region'); $event.stopPropagation()">Fetch this region</button>
              </div>
            }
            @default {
              @for (line of node().detail ?? []; track line) { <p>{{ line }}</p> }
            }
          }
        </div>
      </div>
    }
  `,
})
export class NodeCard {
  private readonly sanitizer = inject(DomSanitizer);

  readonly node = input.required<WorldNode>();
  readonly rank = input.required<RankName>();
  readonly focus = input<boolean>(false);
  /** Set while this node is the destination of an in-flight hop (skeleton). */
  readonly expanding = input<boolean>(false);
  /** The card's size pin (MIN/AUTO/FULL). */
  readonly mode = input<NodeMode>('auto');

  readonly act = output<string>();
  readonly setMode = output<NodeMode>();

  readonly state = computed<NodeState>(() => this.node().state ?? 'ready');

  /** The prototype's verbatim body for this node, if it has one. */
  readonly bodyHtml = computed<SafeHtml | null>(() => {
    const raw = NODE_BODIES[this.node().id];
    return raw ? this.sanitizer.bypassSecurityTrustHtml(raw) : null;
  });

  /** data-show: skeleton while arriving; then the pin; then focus=full. */
  readonly show = computed(() => {
    if (this.expanding()) return 'skel';
    const m = this.mode();
    if (m === 'min') return 'recap';
    if (m === 'full') return 'full';
    return this.focus() ? 'full' : 'recap';
  });

  readonly ariaLabel = computed(() => {
    const n = this.node();
    return `${n.kindLabel} · ${n.name} · ${n.recap}${this.focus() ? ' · you are here' : ''}`;
  });
}
