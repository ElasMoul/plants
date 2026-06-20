import { Component, Inject, Optional, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { PhotoUploadComponent } from '../photo-upload/photo-upload.component';

export interface IdentificationUploadDialogData {
  plantId?: number;
  plantNickname?: string;
  // Species-seeded context (Flow 2 entry point, T6.6) — opens the dialog with the right title.
  // Submission still goes through the existing unlinked-scan path; threading speciesId into the
  // actual identification request and skipping species-confirmation is T6.9's job, not this one's.
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
    @Optional() @Inject(MAT_DIALOG_DATA) readonly data: IdentificationUploadDialogData | null,
  ) {}

  get title(): string {
    if (this.data?.plantNickname) {
      return `Add a scan for ${this.data.plantNickname}`;
    }
    if (this.data?.speciesName) {
      return `Add a plant of ${this.data.speciesName}`;
    }
    return 'Identify a Plant';
  }

  startIdentification(): void {
    this.photoUpload?.onAnalyze();
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.dialogRef.close(payload);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
