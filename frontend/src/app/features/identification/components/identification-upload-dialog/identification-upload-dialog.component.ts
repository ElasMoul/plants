import { Component, ViewChild } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { AnalyzeEmitPayload } from '../../models/identification.model';
import { PhotoUploadComponent } from '../photo-upload/photo-upload.component';

@Component({
  selector: 'app-identification-upload-dialog',
  templateUrl: './identification-upload-dialog.component.html',
  styleUrls: ['./identification-upload-dialog.component.scss'],
})
export class IdentificationUploadDialogComponent {
  @ViewChild(PhotoUploadComponent) photoUpload?: PhotoUploadComponent;

  constructor(
    private readonly dialogRef: MatDialogRef<IdentificationUploadDialogComponent, AnalyzeEmitPayload>,
  ) {}

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
