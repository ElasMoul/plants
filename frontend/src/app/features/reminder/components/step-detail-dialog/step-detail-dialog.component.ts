import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface StepDetailDialogData {
  instruction: string;
  stepDetail: string | null;
  stepDiagramContent: string | null;
}

@Component({
  selector: 'app-step-detail-dialog',
  templateUrl: './step-detail-dialog.component.html',
  styleUrls: ['./step-detail-dialog.component.scss'],
})
export class StepDetailDialogComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<StepDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public readonly data: StepDetailDialogData,
  ) {}

  close(): void {
    this.dialogRef.close();
  }
}
