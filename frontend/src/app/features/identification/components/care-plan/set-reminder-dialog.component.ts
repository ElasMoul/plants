import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface SetReminderDialogData {
  cardTitle: string;
  frequencyDays: number;
}

@Component({
  selector: 'app-set-reminder-dialog',
  templateUrl: './set-reminder-dialog.component.html',
  styleUrls: ['./set-reminder-dialog.component.scss'],
})
export class SetReminderDialogComponent {
  readonly form: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<SetReminderDialogComponent, number>,
    @Inject(MAT_DIALOG_DATA) public readonly data: SetReminderDialogData,
  ) {
    this.form = this.fb.group({
      frequencyDays: [data.frequencyDays, [Validators.required, Validators.min(1)]],
    });
  }

  confirm(): void {
    if (this.form.invalid) return;
    this.dialogRef.close(this.form.value.frequencyDays as number);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
