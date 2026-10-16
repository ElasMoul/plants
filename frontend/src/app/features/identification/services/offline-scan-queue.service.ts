import { Inject, Injectable, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, firstValueFrom } from 'rxjs';
import { translate } from '../../../shared/i18n/language.service';
import { AnalyzeEmitPayload } from '../models/identification.model';
import { IdentificationService } from './identification.service';
import { OFFLINE_SCAN_STORE, OfflineScanStore, QueuedScan } from './offline-scan-store';

export const MAX_UPLOAD_ATTEMPTS = 3;
export const BACKOFF_BASE_MS = 2000;
/** Auth/not-found style errors will not heal by themselves: fail at once so the user can act. */
const NON_RETRYABLE_STATUSES = [401, 403, 404];

/**
 * Keeps scans submitted without a connection on the device (IndexedDB) and
 * uploads them through the normal identification call once the browser is online.
 * The server (and its quota) is only touched at upload time.
 */
@Injectable()
export class OfflineScanQueueService implements OnDestroy {
  private readonly itemsSubject = new BehaviorSubject<QueuedScan[]>([]);
  private readonly uploadedSubject = new Subject<number>();
  private draining = false;
  private timer?: ReturnType<typeof setTimeout>;
  private started = false;
  private readonly onOnline = () => void this.drain();

  readonly items$: Observable<QueuedScan[]> = this.itemsSubject.asObservable();
  /** Emits the identification id of each successfully uploaded scan. */
  readonly uploaded$: Observable<number> = this.uploadedSubject.asObservable();

  constructor(
    private readonly api: IdentificationService,
    @Inject(OFFLINE_SCAN_STORE) private readonly store: OfflineScanStore,
  ) {}

  get items(): QueuedScan[] {
    return this.itemsSubject.value;
  }

  /** Restores the persisted queue, listens for reconnects and drains if online. */
  async start(): Promise<void> {
    if (this.started) {
      return;
    }
    this.started = true;
    window.addEventListener('online', this.onOnline);
    try {
      const stored = await this.store.getAll();
      // An 'uploading' entry means the app closed mid-upload: treat it as waiting again.
      const restored = stored
        .map(s => (s.status === 'uploading' ? { ...s, status: 'waiting' as const } : s))
        .sort((a, b) => a.createdAt - b.createdAt);
      this.itemsSubject.next(restored);
    } catch {
      this.itemsSubject.next([]);
    }
    await this.drain();
  }

  ngOnDestroy(): void {
    window.removeEventListener('online', this.onOnline);
    clearTimeout(this.timer);
  }

  /** Rejects if the scan could not be persisted, so callers never claim a photo is queued when it is not. */
  async enqueue(payload: AnalyzeEmitPayload): Promise<void> {
    const scan: QueuedScan = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      images: payload.images,
      organs: payload.organs,
      plantId: payload.plantId,
      userContext: payload.userContext,
      createdAt: Date.now(),
      status: 'waiting',
      attempts: 0,
    };
    await this.store.put(scan);
    this.itemsSubject.next([...this.items, scan]);
  }

  async retry(id: string): Promise<void> {
    const scan = this.items.find(s => s.id === id);
    if (!scan || scan.status !== 'failed') {
      return;
    }
    await this.update({ ...scan, status: 'waiting', attempts: 0, lastError: undefined, nextAttemptAt: undefined });
    await this.drain();
  }

  async discard(id: string): Promise<void> {
    await this.store.delete(id);
    this.itemsSubject.next(this.items.filter(s => s.id !== id));
  }

  async drain(): Promise<void> {
    if (this.draining || !navigator.onLine) {
      return;
    }
    this.draining = true;
    clearTimeout(this.timer);
    try {
      for (const scan of this.items.filter(s => s.status === 'waiting')) {
        if ((scan.nextAttemptAt ?? 0) > Date.now()) {
          continue;
        }
        if (!(await this.upload(scan))) {
          break;
        }
      }
    } finally {
      this.draining = false;
      this.scheduleBackoffRetry();
    }
  }

  /** @returns false when the drain should stop (connection lost). */
  private async upload(scan: QueuedScan): Promise<boolean> {
    await this.update({ ...scan, status: 'uploading' });
    try {
      const res = await firstValueFrom(
        this.api.analyze(scan.images, scan.organs, scan.plantId, undefined, scan.userContext),
      );
      await this.discard(scan.id);
      this.uploadedSubject.next(res.data.identificationId);
      return true;
    } catch (e) {
      const err = e as HttpErrorResponse;
      if (err.status === 0) {
        // No connection: not the server's fault, so it does not count as an attempt.
        await this.update({ ...scan, status: 'waiting' });
        return false;
      }
      const attempts = scan.attempts + 1;
      const giveUp = attempts >= MAX_UPLOAD_ATTEMPTS || NON_RETRYABLE_STATUSES.includes(err.status);
      await this.update({
        ...scan,
        attempts,
        status: giveUp ? 'failed' : 'waiting',
        lastError: this.describe(err),
        nextAttemptAt: giveUp ? undefined : Date.now() + BACKOFF_BASE_MS * 2 ** (attempts - 1),
      });
      return true;
    }
  }

  private scheduleBackoffRetry(): void {
    const due = this.items
      .filter(s => s.status === 'waiting' && s.nextAttemptAt)
      .map(s => s.nextAttemptAt as number);
    if (due.length) {
      this.timer = setTimeout(() => void this.drain(), Math.max(0, Math.min(...due) - Date.now()));
    }
  }

  private async update(scan: QueuedScan): Promise<void> {
    try {
      await this.store.put(scan);
    } catch {
      /* in-memory state still drives this session; the store keeps the last saved copy */
    }
    this.itemsSubject.next(this.items.map(s => (s.id === scan.id ? scan : s)));
  }

  private describe(err: HttpErrorResponse): string {
    const message = err.error?.message;
    return typeof message === 'string' && message
      ? message
      : `${translate('Upload failed')} (${err.status})`;
  }
}
