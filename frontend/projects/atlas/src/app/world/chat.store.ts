import { computed, inject, Injectable, signal } from '@angular/core';
import { DeviceStore, type DataSource } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import type { ChatFailure, ChatThreadDto, ChatTurnDto } from './world.dto';
import { threadKey } from './world.dto';

/** A thread never grows past this, whatever the reader asks. */
export const MAX_TURNS_PER_THREAD = 20;

/** The answer being written right now — one companion answers one question. */
export interface StreamingAsk {
  key: string;
  question: string;
  text: string;
  plantId?: number;
}

/**
 * The companion's threads and the one answer in flight.
 *
 * The server keeps no conversation, so this is the atlas's own memory. It is
 * namespaced by data source exactly as DeviceStore is — a mock thread must never
 * appear beside a live garden — and it is persisted only when
 * `ai.chatThreads` is 'device'; on 'session' it lives for as long as the page
 * does, which is the classic app's behaviour.
 *
 * Nothing here touches the world graph: no updateWorld, no reloadRequested, no
 * layout epoch. An answer arriving must never move the camera.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly device = inject(DeviceStore);
  private readonly settings = inject(SettingsStore);

  private readonly threadsByKey = signal<Record<string, ChatThreadDto>>({});
  /** The source these threads were loaded for, so a switch reloads rather than mixes. */
  private loadedFor: DataSource | null = null;

  readonly streaming = signal<StreamingAsk | null>(null);
  readonly failures = signal<Record<string, ChatFailure>>({});
  readonly expanded = signal<Record<string, boolean>>({});

  /** Every thread, newest-touched first. */
  readonly threads = computed<ChatThreadDto[]>(() =>
    Object.values(this.threadsByKey()).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
  );

  /** Reads the stored threads for a source; call once per source, on boot. */
  load(source: DataSource): void {
    this.loadedFor = source;
    if (this.settings.get('ai.chatThreads') !== 'device') {
      // The reader asked for threads not to outlive the page, so what a previous
      // 'device' run wrote is removed rather than left to resurface on a switch back.
      this.forgetDevice(source);
      this.threadsByKey.set({});
      return;
    }
    const out: Record<string, ChatThreadDto> = {};
    for (const thread of this.device.chat(source)) out[thread.key] = thread;
    this.threadsByKey.set(out);
  }

  thread(key: string): ChatThreadDto | undefined {
    return this.threadsByKey()[key];
  }

  turns(key: string): ChatTurnDto[] {
    return this.threadsByKey()[key]?.turns ?? [];
  }

  /** Commits one finished turn — answered, truncated or refused. */
  append(
    turn: ChatTurnDto,
    context: { plantId?: number; plantName?: string } = {},
  ): void {
    const key = threadKey(context.plantId ?? turn.plantId);
    const existing = this.threadsByKey()[key];
    const next: ChatThreadDto = {
      key,
      plantId: context.plantId ?? turn.plantId ?? existing?.plantId,
      plantName: context.plantName ?? existing?.plantName,
      turns: [...(existing?.turns ?? []), turn].slice(-MAX_TURNS_PER_THREAD),
      updatedAt: turn.askedAt,
    };
    this.commit({ ...this.threadsByKey(), [key]: next });
  }

  forget(key: string): void {
    const all = { ...this.threadsByKey() };
    delete all[key];
    this.commit(all);
  }

  clear(): void {
    this.commit({});
  }

  // ---------- the answer in flight ----------

  begin(key: string, question: string, plantId?: number): void {
    this.streaming.set({ key, question, text: '', plantId });
    const failures = { ...this.failures() };
    delete failures[key];
    this.failures.set(failures);
  }

  token(text: string): void {
    const now = this.streaming();
    if (!now) return;
    this.streaming.set({ ...now, text: now.text + text });
  }

  /** The answer stopped, however it stopped. Returns the text written so far. */
  end(): string {
    const now = this.streaming();
    this.streaming.set(null);
    return now?.text ?? '';
  }

  fail(key: string, failure: ChatFailure): void {
    this.failures.set({ ...this.failures(), [key]: failure });
  }

  failure(key: string): ChatFailure | undefined {
    return this.failures()[key];
  }

  /** "Read the whole thread" — a focus-only expansion, never a node or a route. */
  toggleExpanded(key: string): void {
    this.expanded.set({ ...this.expanded(), [key]: !this.expanded()[key] });
  }

  isExpanded(key: string): boolean {
    return this.expanded()[key] === true;
  }

  private commit(all: Record<string, ChatThreadDto>): void {
    const kept = Number(this.settings.get('data.chatThreadsKept')) || 0;
    const ordered = Object.values(all).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    const trimmed: Record<string, ChatThreadDto> = {};
    for (const thread of ordered.slice(0, Math.max(0, kept))) trimmed[thread.key] = thread;
    this.threadsByKey.set(trimmed);
    const source = this.loadedFor ?? (this.settings.get('data.source') as DataSource);
    if (this.settings.get('ai.chatThreads') !== 'device') {
      // On 'session' anything a previous 'device' run left is dropped, so the
      // reader's choice actually removes it — but nothing is written if nothing stands.
      this.forgetDevice(source);
      return;
    }
    this.device.setChat(source, Object.values(trimmed));
  }

  /** Empties the stored slice for a source, writing only when something is there. */
  private forgetDevice(source: DataSource): void {
    if (this.device.chat(source).length > 0) this.device.setChat(source, []);
  }
}
