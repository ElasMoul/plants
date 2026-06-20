import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ReminderResponse } from '../../../features/reminder/models/reminder.model';
import { ReminderService } from '../../../features/reminder/services/reminder.service';
import { StepDetailDialogComponent } from '../step-detail-dialog/step-detail-dialog.component';

// Shared step-list + diagram block used by both the generic TreatmentPlan page
// (reminder/pages/treatment-plan-detail) and the disease-level Treatment page
// (treatment/pages/treatment-detail) — extracted in T6.12 since both pages render
// the same diagram/step-list/mark-done/step-detail-dialog UI for a TreatmentPlan's steps.
@Component({
  selector: 'app-treatment-step-list',
  templateUrl: './treatment-step-list.component.html',
  styleUrls: ['./treatment-step-list.component.scss'],
})
export class TreatmentStepListComponent {
  @Input() steps: ReminderResponse[] = [];
  @Input() diagramContent: string | null = null;

  // Fired after a step is successfully marked done — the parent owns reloading the plan.
  @Output() stepCompleted = new EventEmitter<void>();

  markingDoneId: number | null = null;

  constructor(
    private readonly dialog: MatDialog,
    private readonly reminderService: ReminderService,
  ) {}

  get completedCount(): number {
    return this.steps.filter(s => !s.enabled).length;
  }

  get totalCount(): number {
    return this.steps.length;
  }

  stepInstruction(step: ReminderResponse): string {
    return step.instruction ?? step.careType;
  }

  hasStepDetail(step: ReminderResponse): boolean {
    return !!step.stepDetail || !!step.stepDiagramContent;
  }

  openStepDetail(step: ReminderResponse): void {
    this.dialog.open(StepDetailDialogComponent, {
      width: '420px',
      data: {
        instruction: this.stepInstruction(step),
        stepDetail: step.stepDetail ?? null,
        stepDiagramContent: step.stepDiagramContent ?? null,
      },
    });
  }

  dueLabel(step: ReminderResponse): string {
    const diffMs = new Date(step.nextDueAt).getTime() - Date.now();
    const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays < 0) {
      const overdueDays = Math.abs(diffDays);
      return `Overdue by ${overdueDays} day${overdueDays !== 1 ? 's' : ''}`;
    }
    if (diffDays === 0) return 'Due today';
    return `Due in ${diffDays} day${diffDays !== 1 ? 's' : ''}`;
  }

  markStepDone(step: ReminderResponse): void {
    if (this.markingDoneId !== null) return;
    this.markingDoneId = step.id;
    this.reminderService.markCareDone(step.id).subscribe({
      next: () => {
        this.markingDoneId = null;
        this.stepCompleted.emit();
      },
      error: () => {
        this.markingDoneId = null;
      },
    });
  }
}
