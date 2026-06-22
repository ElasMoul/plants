import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject } from 'rxjs';
import { AiErrorService } from '../../../core/services/ai-error.service';
import { IdentificationService } from './identification.service';

export type BatchItemStatus = 'PENDING' | 'SCANNING' | 'DONE' | 'FAILED';

export interface BatchItem {
  id: number;
  file: File;
  preview: string;
  status: BatchItemStatus;
  errorMessage?: string;
}

// Owns the batch-scan queue independently of IdentificationUploadDialogComponent's lifecycle.
// Provided once in identification.module.ts (not root, but the lazy module's injector — and
// this service instance — lives for the rest of the SPA session once loaded, same as any other
// Angular lazy module). Closing the dialog, navigating elsewhere, or reopening the dialog later
// must never abort or lose queued/in-flight items — that was the bug in the dialog-owned version
// of this queue (its subscriptions died with the dialog's own destroy$).
@Injectable()
export class BatchScanService {
  private readonly itemsSubject = new BehaviorSubject<BatchItem[]>([]);
  readonly items$ = this.itemsSubject.asObservable();

  running = false;

  private nextId = 0;

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly aiErrorService: AiErrorService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  get items(): BatchItem[] {
    return this.itemsSubject.value;
  }

  get done(): boolean {
    const items = this.itemsSubject.value;
    return items.length > 0 && items.every(i => i.status === 'DONE' || i.status === 'FAILED');
  }

  get hasFailures(): boolean {
    return this.itemsSubject.value.some(i => i.status === 'FAILED');
  }

  start(files: { file: File; preview: string }[]): void {
    this.itemsSubject.next(
      files.map(f => ({ id: this.nextId++, file: f.file, preview: f.preview, status: 'PENDING' as BatchItemStatus })),
    );
    this.running = true;
    this.runQueue();
  }

  retryFailed(): void {
    this.patchWhere(i => i.status === 'FAILED', { status: 'PENDING', errorMessage: undefined });
    this.running = true;
    this.runQueue();
  }

  // A finished batch's rows stay visible (so reopening the dialog shows what happened) until the
  // user explicitly starts a new one.
  reset(): void {
    this.itemsSubject.next([]);
    this.running = false;
  }

  // Sequential by design (T7.3) — a 429 partway through means every later item would 429 too, so
  // there's no point firing them all at once; one-at-a-time also keeps the per-item status list
  // meaningful (item N+1 hasn't even been tried while item N is still in flight).
  private runQueue(): void {
    const next = this.itemsSubject.value.find(i => i.status === 'PENDING');
    if (!next) {
      this.running = false;
      this.notifyIfDone();
      return;
    }

    const id = next.id;
    this.patchItem(id, { status: 'SCANNING' });
    this.identificationService.analyze([next.file], ['auto']).subscribe({
      next: res => {
        this.identificationService.pollUntilComplete(res.data.identificationId).subscribe({
          next: () => {
            this.patchItem(id, { status: 'DONE' });
            this.runQueue();
          },
          error: () => {
            this.patchItem(id, { status: 'FAILED', errorMessage: 'Analysis failed — please try again' });
            this.runQueue();
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.patchItem(id, { status: 'FAILED', errorMessage: this.aiErrorService.handle(err) });
        this.runQueue();
      },
    });
  }

  // Fires whether or not the dialog is even open — the queue runs entirely independently of it,
  // so this is the only feedback the user gets if they closed the dialog or navigated away.
  private notifyIfDone(): void {
    const items = this.itemsSubject.value;
    if (!items.length) return;
    const doneCount = items.filter(i => i.status === 'DONE').length;
    const failedCount = items.filter(i => i.status === 'FAILED').length;
    const message = failedCount
      ? `Batch scan finished — ${doneCount} added, ${failedCount} failed`
      : `Batch scan finished — ${doneCount} plant${doneCount === 1 ? '' : 's'} added`;
    const ref = this.snackBar.open(message, 'View', { duration: 6000 });
    ref.onAction().subscribe(() => this.router.navigate(['/identify']));
  }

  private patchItem(id: number, patch: Partial<BatchItem>): void {
    this.patchWhere(i => i.id === id, patch);
  }

  private patchWhere(predicate: (item: BatchItem) => boolean, patch: Partial<BatchItem>): void {
    this.itemsSubject.next(this.itemsSubject.value.map(i => (predicate(i) ? { ...i, ...patch } : i)));
  }
}
