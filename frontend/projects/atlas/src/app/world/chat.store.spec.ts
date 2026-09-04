import { TestBed } from '@angular/core/testing';
import { DEVICE_KEY, DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import { ChatStore } from './chat.store';
import { threadKey, type ChatTurnDto } from './world.dto';

function make(): { chat: ChatStore; settings: SettingsStore; device: DeviceStore } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [ChatStore, DeviceStore, SettingsStore] });
  return {
    chat: TestBed.inject(ChatStore),
    settings: TestBed.inject(SettingsStore),
    device: TestBed.inject(DeviceStore),
  };
}

function turn(n: number, plantId?: number): ChatTurnDto {
  return {
    id: `t${n}`,
    askedAt: new Date(Date.UTC(2026, 8, 4, 9, n)).toISOString(),
    question: `q${n}`,
    reply: `a${n}`,
    plantId,
    outcome: 'answered',
  };
}

describe('ChatStore', () => {
  beforeEach(() => localStorage.clear());

  it('keys a thread by its plant, or by the garden', () => {
    expect(threadKey()).toBe('garden');
    expect(threadKey(4)).toBe('plant:4');
    const { chat } = make();
    chat.load('mock');
    chat.append(turn(1), { plantId: 4, plantName: 'Studio Fig' });
    chat.append(turn(2));
    expect(chat.turns('plant:4')).toHaveLength(1);
    expect(chat.thread('plant:4')?.plantName).toBe('Studio Fig');
    expect(chat.turns('garden')).toHaveLength(1);
  });

  it('keeps mock threads out of the live garden', () => {
    const first = make();
    first.chat.load('mock');
    first.chat.append(turn(1));
    const second = make();
    second.chat.load('live');
    expect(second.chat.turns('garden')).toEqual([]);
    second.chat.load('mock');
    expect(second.chat.turns('garden')).toHaveLength(1);
  });

  it('caps turns at twenty and threads at the setting', () => {
    const { chat, settings } = make();
    settings.set('data.chatThreadsKept', 3);
    chat.load('live');
    for (let n = 1; n <= 25; n++) chat.append(turn(n));
    expect(chat.turns('garden')).toHaveLength(20);
    expect(chat.turns('garden')[0].question).toBe('q6');
    for (const plantId of [1, 2, 3, 4]) chat.append(turn(plantId, plantId), { plantId });
    expect(chat.threads()).toHaveLength(3);
    expect(chat.thread('plant:1')).toBeUndefined();
  });

  it('writes nothing to the device when threads are session-only', () => {
    const { chat, settings } = make();
    settings.set('ai.chatThreads', 'session');
    chat.load('live');
    chat.append(turn(1));
    expect(chat.turns('garden')).toHaveLength(1);
    expect(localStorage.getItem(DEVICE_KEY)).toBeNull();
    expect(make().chat.turns('garden')).toEqual([]);
  });

  it('drops what the device already holds when threads turn session-only', () => {
    const first = make();
    first.chat.load('live');
    first.chat.append(turn(1));
    expect(localStorage.getItem(DEVICE_KEY)).not.toBeNull();

    const second = make();
    second.settings.set('ai.chatThreads', 'session');
    second.chat.load('live');
    expect(second.chat.threads()).toEqual([]);

    // Switching back to 'device' finds nothing, because the choice removed it.
    const third = make();
    third.settings.set('ai.chatThreads', 'device');
    third.chat.load('live');
    expect(third.chat.threads()).toEqual([]);
  });

  it('persists into the source it was loaded for, not the settings default', () => {
    const { chat, settings } = make();
    settings.set('data.source', 'live');
    chat.load('mock');
    chat.append(turn(1));
    const back = make();
    back.chat.load('mock');
    expect(back.chat.turns('garden')).toHaveLength(1);
    back.chat.load('live');
    expect(back.chat.turns('garden')).toEqual([]);
  });

  it('degrades a garbled stored thread to nothing at all', () => {
    localStorage.setItem(
      DEVICE_KEY,
      JSON.stringify({ v: 1, chat: { live: { threads: [{ nope: true }, 7] }, mock: 'rubbish' } }),
    );
    const { chat } = make();
    chat.load('live');
    expect(chat.threads()).toEqual([]);
    chat.load('mock');
    expect(chat.threads()).toEqual([]);
  });

  it('holds one answer in flight and hands back what was written', () => {
    const { chat } = make();
    chat.load('live');
    chat.begin('garden', 'why?');
    chat.token('Draught, ');
    chat.token('most likely.');
    expect(chat.streaming()?.text).toBe('Draught, most likely.');
    expect(chat.end()).toBe('Draught, most likely.');
    expect(chat.streaming()).toBeNull();
    expect(chat.end()).toBe('');
  });

  it('remembers a failure per thread and forgets it when the next ask begins', () => {
    const { chat } = make();
    chat.load('live');
    chat.fail('garden', { kind: 'rate-limited', retryAfterSeconds: null });
    expect(chat.failure('garden')?.kind).toBe('rate-limited');
    chat.begin('garden', 'again?');
    expect(chat.failure('garden')).toBeUndefined();
  });

  it('expands one thread in place and folds it again', () => {
    const { chat } = make();
    expect(chat.isExpanded('garden')).toBe(false);
    chat.toggleExpanded('garden');
    expect(chat.isExpanded('garden')).toBe(true);
    chat.toggleExpanded('garden');
    expect(chat.isExpanded('garden')).toBe(false);
  });

  it('forgets one thread and then all of them', () => {
    const { chat } = make();
    chat.load('live');
    chat.append(turn(1));
    chat.append(turn(2, 3), { plantId: 3 });
    chat.forget('garden');
    expect(chat.thread('garden')).toBeUndefined();
    chat.clear();
    expect(chat.threads()).toEqual([]);
    expect(make().device.chat('live')).toEqual([]);
  });
});
