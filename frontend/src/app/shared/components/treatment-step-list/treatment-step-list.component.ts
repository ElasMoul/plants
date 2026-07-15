import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReminderResponse } from '../../../features/reminder/models/reminder.model';
import { ReminderService } from '../../../features/reminder/services/reminder.service';

// Shared step-list + diagram block used by both the generic TreatmentPlan page
// (reminder/pages/treatment-plan-detail) and the disease-level Treatment page
// (treatment/pages/treatment-detail) — extracted in T6.12 since both pages render
// the same diagram/step-list/mark-done/step-detail UI for a TreatmentPlan's steps.
@Component({
  selector: 'app-treatment-step-list',
  templateUrl: './treatment-step-list.component.html',
  styleUrls: ['./treatment-step-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TreatmentStepListComponent {
  @Input() steps: ReminderResponse[] = [];
  @Input() diagramContent: string | null = null;
  @Input() returnUrl?: string;

  // Fired after a step is successfully marked done — the parent owns reloading the plan.
  @Output() stepCompleted = new EventEmitter<void>();

  markingDoneId: number | null = null;

  constructor(
    private readonly router: Router,
    private readonly reminderService: ReminderService,
    private readonly cdr: ChangeDetectorRef,
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
    if (!step.treatmentPlanId) return;
    const extras = this.returnUrl ? { queryParams: { returnUrl: this.returnUrl } } : {};
    this.router.navigate(['/plans', step.treatmentPlanId, 'steps', step.id], extras);
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

  trackByStepId(_index: number, step: ReminderResponse): number {
    return step.id;
  }

  markStepDone(step: ReminderResponse): void {
    if (this.markingDoneId !== null || !step.enabled) return;
    this.markingDoneId = step.id;
    this.reminderService.markCareDone(step.id).subscribe({
      next: () => {
        this.markingDoneId = null;
        this.stepCompleted.emit();
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        this.markingDoneId = null;
        if (err.status === 400) {
          // Already completed (race with another surface, e.g. the reminder list) — the step
          // really is done, just re-sync the parent's view of it instead of leaving it stuck.
          this.stepCompleted.emit();
        }
        this.cdr.markForCheck();
      },
    });
  }
}
