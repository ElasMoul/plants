import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PlantService } from '../../../plant/services/plant.service';
import { PlantResponse } from '../../../plant/models/plant.model';
import { ReminderService } from '../../services/reminder.service';
import { CareType } from '../../models/reminder.model';

@Component({
  selector: 'app-create-reminder-form',
  templateUrl: './create-reminder-form.component.html',
  styleUrls: ['./create-reminder-form.component.scss'],
})
export class CreateReminderFormComponent implements OnInit, OnDestroy {
  readonly careTypes: { value: CareType; label: string }[] = [
    { value: 'WATERING', label: 'Watering' },
    { value: 'FERTILIZING', label: 'Fertilizing' },
    { value: 'REPOTTING', label: 'Repotting' },
    { value: 'PRUNING', label: 'Pruning' },
  ];

  form!: FormGroup;
  plants: PlantResponse[] = [];
  saving = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly plantService: PlantService,
    private readonly reminderService: ReminderService,
    private readonly snackBar: MatSnackBar,
    private readonly dialogRef: MatDialogRef<CreateReminderFormComponent, boolean>,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      plantId: [null, Validators.required],
      careType: ['WATERING', Validators.required],
      frequencyDays: [7, [Validators.required, Validators.min(1)]],
      firstDueAt: [new Date(), Validators.required],
    });

    this.plantService.getPlants(0, 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => { this.plants = res.data.content; },
        error: () => { this.plants = []; },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSave(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;

    const { plantId, careType, frequencyDays, firstDueAt } = this.form.value as {
      plantId: number;
      careType: CareType;
      frequencyDays: number;
      firstDueAt: Date;
    };

    this.reminderService.createReminder({
      plantId,
      careType,
      frequencyDays,
      firstDueAt: new Date(firstDueAt).toISOString(),
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.saving = false;
        this.snackBar.open('Could not create reminder — please try again.', 'Dismiss', { duration: 4000 });
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
