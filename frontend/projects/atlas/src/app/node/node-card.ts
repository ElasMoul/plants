import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RankName } from '@plantpal/rhizome-engine';
import { askFailureCopy } from '../world/ask-copy';
import type { ChatFailure, ChatTurnDto } from '../world/world.dto';
import { NodeState, WorldNode } from '../world/world.model';
import { NODE_BODIES } from '../world/world.bodies';

/** The answer being written right now, as this card should show it. */
export interface StreamingTurn {
  question: string;
  text: string;
}

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
    '[attr.data-pending]': 'pending() ? true : null',
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
            @case ('archived') {
              <div class="state">
                <p class="state__note">Archived — still readable.</p>
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
  private readonly host = inject(ElementRef<HTMLElement>);

  constructor() {
    // The answer in flight is painted into the two rows the body already reserved
    // for it (data-streaming-q / data-streaming), never by re-rendering the body:
    // re-assembly is what moves a card, and an answer arriving must move nothing.
    effect(() => {
      const turn = this.streamingTurn();
      const turns = this.chatTurns();
      const failure = this.chatFailure();
      const expanded = this.chatExpanded();
      this.node();
      queueMicrotask(() => this.paintChat(turn, turns, failure, expanded));
    });
  }

  readonly node = input.required<WorldNode>();
  readonly rank = input.required<RankName>();
  readonly focus = input<boolean>(false);
  /** Set while this node is the destination of an in-flight hop (skeleton). */
  readonly expanding = input<boolean>(false);
  /** The card's size pin (MIN/AUTO/FULL). */
  readonly mode = input<NodeMode>('auto');
  /** Data outstanding (slow probe / live loading) — shows the .pending block. */
  readonly pending = input<boolean>(false);
  /** The answer being written into this card's feed, if one is. */
  readonly streamingTurn = input<StreamingTurn | null>(null);
  /** Every turn of the thread this card shows — the tail beyond what the body
   *  already drew is painted in place, so a finished answer needs no reload. */
  readonly chatTurns = input<ChatTurnDto[]>([]);
  /** Why the last ask did not answer, if it did not. */
  readonly chatFailure = input<ChatFailure | null>(null);
  /** Whether the reader asked to read the whole thread rather than its tail. */
  readonly chatExpanded = input<boolean>(false);

  readonly act = output<string>();
  readonly setMode = output<NodeMode>();

  readonly state = computed<NodeState>(() => this.node().state ?? 'ready');

  /** The prototype's verbatim body for this node, if it has one. */
  readonly bodyHtml = computed<SafeHtml | null>(() => {
    const raw = this.node().body ?? NODE_BODIES[this.node().id];
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

  /**
   * Paint the companion's live material into the rows and the panel the body
   * already reserved for it: the answer being written, the turns committed since
   * this body was built, and why an ask did not answer. Text and one pair of rows
   * per late turn — the card's own material, never a re-assembly of the world.
   */
  private paintChat(
    turn: StreamingTurn | null,
    turns: ChatTurnDto[],
    failure: ChatFailure | null,
    expanded: boolean,
  ): void {
    const root = this.host.nativeElement as HTMLElement;
    const feed = root.querySelector<HTMLElement>('.feed[data-thread-rendered]');
    const panel = root.querySelector<HTMLElement>('[data-chat-failure]');
    if (panel) {
      const copy = failure ? askFailureCopy(failure) : null;
      const title = panel.querySelector<HTMLElement>('[data-chat-failure-title]');
      const note = panel.querySelector<HTMLElement>('[data-chat-failure-note]');
      if (copy) {
        if (title) title.textContent = copy.title;
        if (note) note.textContent = copy.note;
      }
      panel.hidden = !copy;
    }
    if (!feed) return;
    // "Read the whole thread": the same feed, widened. No node, no route, no camera.
    for (const row of Array.from(feed.querySelectorAll<HTMLElement>('[data-extra]'))) {
      row.hidden = !expanded;
    }
    const toggle = Array.from(root.querySelectorAll<HTMLElement>('.stake--quiet')).find(b =>
      /^(Read the whole thread|Show just the recent turns)$/.test(b.textContent?.trim() ?? ''),
    );
    if (toggle) toggle.textContent = expanded ? 'Show just the recent turns' : 'Read the whole thread';
    const q = feed.querySelector<HTMLElement>('[data-streaming-q]');
    const a = feed.querySelector<HTMLElement>('[data-streaming]');
    // the turns this body did not draw, drawn here in the same material
    const already = Number(feed.dataset['threadRendered'] ?? '0');
    for (const late of Array.from(feed.querySelectorAll('[data-late]'))) late.remove();
    for (const t of turns.slice(already)) {
      const when = t.askedAt.slice(11, 16);
      for (const [who, text] of [
        ['you', t.question],
        ['PlantPal', t.reply],
      ] as const) {
        const row = document.createElement('div');
        row.className = 'feed__row';
        row.setAttribute('data-late', '');
        const w = document.createElement('span');
        w.className = 'feed__when';
        w.textContent = when;
        const s = document.createElement('span');
        s.textContent = who;
        const v = document.createElement('span');
        v.className = 'feed__val';
        v.textContent = text;
        row.append(w, s, v);
        if (q) feed.insertBefore(row, q);
        else feed.append(row);
      }
    }
    if (!q || !a) return;
    const qVal = q.querySelector<HTMLElement>('.feed__val');
    const aVal = a.querySelector<HTMLElement>('.feed__val');
    if (qVal) qVal.textContent = turn ? turn.question : '';
    if (aVal) aVal.textContent = turn ? turn.text : '';
    q.hidden = !turn;
    a.hidden = !turn;
  }

  readonly ariaLabel = computed(() => {
    const n = this.node();
    return `${n.kindLabel} · ${n.name} · ${n.recap}${this.focus() ? ' · you are here' : ''}`;
  });
}
