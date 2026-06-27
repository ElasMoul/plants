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
  userContext?: string;
}

// Owns the batch-scan queue independently of IdentificationUploadDialogComponent's lifecycle —
// closing the dialog, navigating elsewhere, or reopening the dialog later must never abort or
// lose queued/in-flight items (that was a real shipped bug: the queue used to live in the dialog
// component, piped through its own takeUntil(this.destroy$), so closing the dialog killed it).
//
// NOT providedIn: 'root' — same convention as every other feature service (see FRONTEND.md).
// IdentificationUploadDialogComponent is opened from 4 different lazy modules
// (identification/plant/species/dashboard), each of which must list this in its own `providers:`
// array, exactly like they already do for IdentificationService — a root-provided singleton here
// would also be unable to constructor-inject IdentificationService (itself module-scoped, not
// root), since root-level services are constructed via the root injector alone.
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

  start(files: { file: File; preview: string }[], userContext?: string): void {
    const items = files.map(f => ({
      id: this.nextId++, file: f.file, preview: f.preview,
      status: 'PENDING' as BatchItemStatus,
      userContext,
    }));
    this.itemsSubject.next(items);
    this.running = true;
    items.forEach(item => this.processItem(item.id));
  }

  retryFailed(): void {
    const failedIds = this.itemsSubject.value.filter(i => i.status === 'FAILED').map(i => i.id);
    this.patchWhere(i => i.status === 'FAILED', { status: 'PENDING', errorMessage: undefined });
    this.running = true;
    failedIds.forEach(id => this.processItem(id));
  }

  // A finished batch's rows stay visible (so reopening the dialog shows what happened) until the
  // user explicitly starts a new one.
  reset(): void {
    this.itemsSubject.next([]);
    this.running = false;
  }

  // Concurrent, not sequential — every item's /analyze call fires immediately so each
  // identification is created on the backend (and shows up as PENDING in the identify list)
  // right away, instead of the 2nd one not even existing server-side until the 1st fully
  // resolves. A batch is capped at MAX_IMAGES (5, see PhotoUploadComponent), well under the
  // per-user identification rate limit (20/hour), so firing them together is safe; a 429 on any
  // one of them still just fails that item via AiErrorService, same as before.
  private processItem(id: number): void {
    const item = this.itemsSubject.value.find(i => i.id === id);
    if (!item) return;

    this.patchItem(id, { status: 'SCANNING' });
    this.identificationService.analyze([item.file], ['auto'], undefined, undefined, item.userContext).subscribe({
      next: res => {
        this.identificationService.pollUntilComplete(res.data.identificationId).subscribe({
          next: () => {
            this.patchItem(id, { status: 'DONE' });
            this.checkAllResolved();
          },
          error: () => {
            this.patchItem(id, { status: 'FAILED', errorMessage: 'Analysis failed — please try again' });
            this.checkAllResolved();
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.patchItem(id, { status: 'FAILED', errorMessage: this.aiErrorService.handle(err) });
        this.checkAllResolved();
      },
    });
  }

  private checkAllResolved(): void {
    const items = this.itemsSubject.value;
    const allResolved = items.length > 0 && items.every(i => i.status === 'DONE' || i.status === 'FAILED');
    if (!allResolved) return;
    this.running = false;
    this.notifyIfDone();
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
