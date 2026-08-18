import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CareLogResponse } from '../../models/care-log.model';
import { careIcon } from '../../models/care-icon.util';

@Component({
    selector: 'app-care-log-detail-dialog',
    templateUrl: './care-log-detail-dialog.component.html',
    styleUrls: ['./care-log-detail-dialog.component.scss'],
    standalone: false
})
export class CareLogDetailDialogComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<CareLogDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public readonly data: CareLogResponse,
  ) {}

  get icon(): string {
    return careIcon(this.data.careType);
  }

  close(): void {
    this.dialogRef.close();
  }
}
