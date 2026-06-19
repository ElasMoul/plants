import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { parseDetailAsList, ParsedDetail } from '../../../../shared/utils/detail-list.util';

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
  readonly detailList: ParsedDetail | null;

  constructor(
    private readonly dialogRef: MatDialogRef<StepDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public readonly data: StepDetailDialogData,
  ) {
    this.detailList = parseDetailAsList(data.stepDetail);
  }

  close(): void {
    this.dialogRef.close();
  }
}
