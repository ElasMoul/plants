import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NodeCard, type StreamingTurn } from './node-card';
import type { ChatFailure, ChatTurnDto } from '../world/world.dto';
import type { WorldNode } from '../world/world.model';

/** The companion's body, in the shape the assembly writes it. */
const ASK_BODY = `<div class="n__recap"><p class="n__recap-line">Ask about your garden</p></div>
  <div class="n__full">
    <section class="state state--error" data-chat-failure hidden>
      <div class="state__head"><h4 class="state__title" data-chat-failure-title></h4></div>
      <p class="state__note" data-chat-failure-note></p>
    </section>
    <section class="state">
      <div class="feed" data-thread="garden" data-thread-rendered="1">
        <div class="feed__row"><span class="feed__when">09:06</span><span>you</span><span class="feed__val">q1</span></div>
        <div class="feed__row" data-extra hidden><span class="feed__when">09:06</span><span>PlantPal</span><span class="feed__val">a1</span></div>
        <div class="feed__row" data-streaming-q hidden><span class="feed__when">now</span><span>you</span><span class="feed__val"></span></div>
        <div class="feed__row" data-streaming hidden><span class="feed__when">now</span><span>PlantPal</span><span class="feed__val"></span></div>
      </div>
      <div class="btn-row"><button class="stake" type="button">Ask something</button><button class="stake stake--quiet" type="button">Read the whole thread</button></div>
    </section>
  </div>`;

const ASK_NODE: WorldNode = {
  id: 'n-ask',
  glyph: '✎',
  cell: { col: 8, row: 5 },
  kind: 'guide',
  kindLabel: 'Companion',
  name: 'Ask PlantPal',
  recap: 'Ask about your garden',
  body: ASK_BODY,
};

function turn(over: Partial<ChatTurnDto> = {}): ChatTurnDto {
  return {
    id: 't2',
    askedAt: '2026-09-04T09:12:00.000Z',
    question: 'how much water?',
    reply: 'a soak, then dry',
    outcome: 'answered',
    ...over,
  };
}

@Component({
  imports: [NodeCard],
  template: `<rz-node
    [node]="node"
    rank="focus"
    [focus]="true"
    [streamingTurn]="streaming()"
    [chatTurns]="turns()"
    [chatFailure]="failure()"
    [chatExpanded]="expanded()"
  />`,
})
class Host {
  readonly node = ASK_NODE;
  readonly streaming = signal<StreamingTurn | null>(null);
  readonly turns = signal<ChatTurnDto[]>([]);
  readonly failure = signal<ChatFailure | null>(null);
  readonly expanded = signal(false);
}

describe('NodeCard — the companion painted in place (C2)', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const rows = (selector: string): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>(selector));

  /** The paint runs in a microtask, after the body binding has landed. */
  const settle = async (): Promise<void> => {
    fixture.detectChanges();
    await Promise.resolve();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    await settle();
  });

  it('leaves the reserved rows hidden and empty while nothing is being written', () => {
    const q = el().querySelector<HTMLElement>('[data-streaming-q]');
    const a = el().querySelector<HTMLElement>('[data-streaming]');
    expect(q?.hidden).toBe(true);
    expect(a?.hidden).toBe(true);
    expect(a?.textContent).not.toContain('how much');
  });

  it('paints the answer being written into the reserved rows, adding no node', async () => {
    const before = rows('.feed__row').length;
    host.streaming.set({ question: 'how much water?', text: 'a soak' });
    await settle();
    const a = el().querySelector<HTMLElement>('[data-streaming] .feed__val');
    expect(a?.textContent).toBe('a soak');
    host.streaming.set({ question: 'how much water?', text: 'a soak, then dry' });
    await settle();
    expect(el().querySelector<HTMLElement>('[data-streaming] .feed__val')?.textContent).toBe(
      'a soak, then dry',
    );
    // a token adds no row: the rows were reserved before the first one arrived
    expect(rows('.feed__row').length).toBe(before);
  });

  it('appends a turn committed after the body was built, in the same material', async () => {
    host.turns.set([turn({ id: 't1', question: 'q1', reply: 'a1' }), turn()]);
    await settle();
    const late = rows('[data-late]');
    expect(late.length).toBe(2); // only the turn the body had not drawn
    expect(late[0].querySelector('.feed__val')?.textContent).toBe('how much water?');
    expect(late[1].querySelector('.feed__val')?.textContent).toBe('a soak, then dry');
    expect(late[0].className).toBe('feed__row');
    // painting twice never doubles the rows
    host.turns.set([turn({ id: 't1', question: 'q1', reply: 'a1' }), turn()]);
    await settle();
    expect(rows('[data-late]').length).toBe(2);
  });

  it('wears a failure in its own panel, never as an overlay', async () => {
    host.failure.set({ kind: 'rate-limited', retryAfterSeconds: null });
    await settle();
    const panel = el().querySelector<HTMLElement>('[data-chat-failure]');
    expect(panel?.hidden).toBe(false);
    expect(panel?.textContent).toContain('You have asked as much as the hour allows');
    expect(panel?.textContent).toContain('it lifts within the hour');
    expect(el().querySelectorAll('[role="dialog"]').length).toBe(0);
    host.failure.set(null);
    await settle();
    expect(el().querySelector<HTMLElement>('[data-chat-failure]')?.hidden).toBe(true);
  });

  it('reads the whole thread by widening the same feed, and says so on the stake', async () => {
    expect(el().querySelector<HTMLElement>('[data-extra]')?.hidden).toBe(true);
    host.expanded.set(true);
    await settle();
    expect(el().querySelector<HTMLElement>('[data-extra]')?.hidden).toBe(false);
    expect(el().querySelector('.stake--quiet')?.textContent).toBe('Show just the recent turns');
    host.expanded.set(false);
    await settle();
    expect(el().querySelector('.stake--quiet')?.textContent).toBe('Read the whole thread');
  });
});
