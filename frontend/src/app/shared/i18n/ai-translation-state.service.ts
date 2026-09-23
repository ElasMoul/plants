import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AiTranslationState {
  readonly pending = signal(0);
  readonly failed = signal<string[]>([]);
  start(): void { this.pending.update(value => value + 1); }
  finish(): void { this.pending.update(value => Math.max(0, value - 1)); }
  fail(id: string): void { this.failed.update(ids => [...new Set([...ids, id])]); }
}
