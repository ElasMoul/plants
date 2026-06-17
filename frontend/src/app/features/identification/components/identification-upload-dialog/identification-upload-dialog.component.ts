import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { AnalyzeEmitPayload } from '../../models/identification.model';

@Component({
  selector: 'app-identification-upload-dialog',
  templateUrl: './identification-upload-dialog.component.html',
  styleUrls: ['./identification-upload-dialog.component.scss'],
})
export class IdentificationUploadDialogComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<IdentificationUploadDialogComponent, AnalyzeEmitPayload>,
  ) {}

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.dialogRef.close(payload);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
