import { Component, Inject, Optional, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { PhotoUploadComponent } from '../photo-upload/photo-upload.component';

export interface IdentificationUploadDialogData {
  plantId?: number;
  plantNickname?: string;
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
    return this.data?.plantNickname
      ? `Add a scan for ${this.data.plantNickname}`
      : 'Identify a Plant';
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
