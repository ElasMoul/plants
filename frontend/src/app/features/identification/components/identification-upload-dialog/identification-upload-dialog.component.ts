import { Component, Inject, OnDestroy, Optional, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AiErrorService } from '../../../../core/services/ai-error.service';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { IdentificationService } from '../../services/identification.service';
import { PhotoUploadComponent } from '../photo-upload/photo-upload.component';

export interface IdentificationUploadDialogData {
  plantId?: number;
  plantNickname?: string;
  // Species-seeded context (Flow 2 entry point, T6.6) — opens the dialog with the right title
  // and threads speciesId into the analyze request (T6.9), so the species-confirm step is
  // skipped entirely for this flow.
  speciesId?: number;
  speciesName?: string;
}

type BatchItemStatus = 'PENDING' | 'SCANNING' | 'DONE' | 'FAILED';

interface BatchItem {
  file: File;
  preview: string;
  status: BatchItemStatus;
  errorMessage?: string;
}

@Component({
  selector: 'app-identification-upload-dialog',
  templateUrl: './identification-upload-dialog.component.html',
  styleUrls: ['./identification-upload-dialog.component.scss'],
})
export class IdentificationUploadDialogComponent implements OnDestroy {
  @ViewChild(PhotoUploadComponent) photoUpload?: PhotoUploadComponent;

  // Batch mode (T7.3) — once started, the photo-upload view is replaced by this per-item
  // progress list; the dialog drives the N independent /analyze calls itself instead of
  // emitting a single combined payload for the parent page to submit (the single-identification
  // flow below this still works exactly as before).
  batchRunning = false;
  batchItems: BatchItem[] = [];

  private batchCancelled = false;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly dialogRef: MatDialogRef<IdentificationUploadDialogComponent, AnalyzeEmitPayload>,
    private readonly identificationService: IdentificationService,
    private readonly aiErrorService: AiErrorService,
    @Optional() @Inject(MAT_DIALOG_DATA) readonly data: IdentificationUploadDialogData | null,
  ) {}

  ngOnDestroy(): void {
    this.batchCancelled = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  get title(): string {
    if (this.batchRunning) {
      return 'Scanning your plants…';
    }
    if (this.data?.plantNickname) {
      return `Add a scan for ${this.data.plantNickname}`;
    }
    if (this.data?.speciesName) {
      return `Add a plant of ${this.data.speciesName}`;
    }
    return 'Identify a Plant';
  }

  get batchDone(): boolean {
    return this.batchItems.length > 0 && this.batchItems.every(i => i.status === 'DONE' || i.status === 'FAILED');
  }

  get batchHasFailures(): boolean {
    return this.batchItems.some(i => i.status === 'FAILED');
  }

  startIdentification(): void {
    if (!this.photoUpload) return;
    if (this.photoUpload.batchMode) {
      this.startBatch(this.photoUpload.entries.map(e => ({ file: e.file, preview: e.preview })));
      return;
    }
    this.photoUpload.onAnalyze();
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.dialogRef.close({ ...payload, speciesId: this.data?.speciesId });
  }

  retryFailed(): void {
    const failed = this.batchItems.filter(i => i.status === 'FAILED');
    failed.forEach(item => { item.status = 'PENDING'; item.errorMessage = undefined; });
    this.runBatchQueue();
  }

  cancel(): void {
    this.batchCancelled = true;
    this.dialogRef.close();
  }

  done(): void {
    this.dialogRef.close();
  }

  private startBatch(files: { file: File; preview: string }[]): void {
    this.batchCancelled = false;
    this.batchItems = files.map(f => ({ file: f.file, preview: f.preview, status: 'PENDING' }));
    this.batchRunning = true;
    this.runBatchQueue();
  }

  // Sequential by design (per T7.3) — a 429 partway through means every later item would 429
  // too, so there's no point firing them all at once; one-at-a-time also keeps the per-item
  // status list meaningful (item N+1 hasn't even been tried while item N is still in flight).
  private runBatchQueue(): void {
    const next = this.batchItems.find(i => i.status === 'PENDING');
    if (!next || this.batchCancelled) return;

    next.status = 'SCANNING';
    this.identificationService.analyze([next.file], ['auto'])
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.identificationService.pollUntilComplete(res.data.identificationId)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
              next: () => {
                next.status = 'DONE';
                this.runBatchQueue();
              },
              error: () => {
                next.status = 'FAILED';
                next.errorMessage = 'Analysis failed — please try again';
                this.runBatchQueue();
              },
            });
        },
        error: (err: HttpErrorResponse) => {
          next.status = 'FAILED';
          next.errorMessage = this.aiErrorService.handle(err);
          this.runBatchQueue();
        },
      });
  }
}
