import { Component, Inject, Optional, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { BatchScanService } from '../../services/batch-scan.service';
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

@Component({
  selector: 'app-identification-upload-dialog',
  templateUrl: './identification-upload-dialog.component.html',
  styleUrls: ['./identification-upload-dialog.component.scss'],
})
export class IdentificationUploadDialogComponent {
  @ViewChild(PhotoUploadComponent) photoUpload?: PhotoUploadComponent;

  constructor(
    private readonly dialogRef: MatDialogRef<IdentificationUploadDialogComponent, AnalyzeEmitPayload>,
    readonly batchScan: BatchScanService,
    @Optional() @Inject(MAT_DIALOG_DATA) readonly data: IdentificationUploadDialogData | null,
  ) {}

  get title(): string {
    if (this.batchActive) {
      return this.batchScan.running ? 'Scanning your plants…' : 'Batch scan';
    }
    if (this.data?.plantNickname) {
      return `Add a scan for ${this.data.plantNickname}`;
    }
    if (this.data?.speciesName) {
      return `Add a plant of ${this.data.speciesName}`;
    }
    return 'Identify a Plant';
  }

  // True once a batch has been started — including after it's finished, so reopening the
  // dialog shows what happened instead of a blank upload form (the queue itself runs
  // independently of this dialog's lifecycle, see BatchScanService).
  get batchActive(): boolean {
    return this.batchScan.items.length > 0;
  }

  startIdentification(): void {
    if (!this.photoUpload) return;
    if (this.photoUpload.batchMode) {
      this.batchScan.start(this.photoUpload.entries.map(e => ({ file: e.file, preview: e.preview })));
      return;
    }
    this.photoUpload.onAnalyze();
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.dialogRef.close({ ...payload, speciesId: this.data?.speciesId });
  }

  retryFailed(): void {
    this.batchScan.retryFailed();
  }

  startNewBatch(): void {
    this.batchScan.reset();
  }

  // Closing here never aborts the batch — it keeps running in BatchScanService regardless, and
  // the user gets a snackbar when it finishes even if this dialog isn't open anymore.
  cancel(): void {
    this.dialogRef.close();
  }

  done(): void {
    this.dialogRef.close();
  }
}
