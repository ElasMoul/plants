import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TreatmentPlanService } from '../../services/treatment-plan.service';
import { ReminderService } from '../../services/reminder.service';
import { ReminderResponse } from '../../models/reminder.model';
import { TreatmentPlanResponse } from '../../models/treatment-plan.model';

type PageState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-treatment-plan-detail',
  templateUrl: './treatment-plan-detail.component.html',
  styleUrls: ['./treatment-plan-detail.component.scss'],
})
export class TreatmentPlanDetailComponent implements OnInit, OnDestroy {
  readonly skeletonSteps = [1, 2, 3];

  state: PageState = 'loading';
  plan: TreatmentPlanResponse | null = null;
  markingDoneId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly reminderService: ReminderService,
  ) {}

  ngOnInit(): void {
    this.loadPlan();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get completedCount(): number {
    return this.plan?.steps.filter(s => !s.enabled).length ?? 0;
  }

  get totalCount(): number {
    return this.plan?.steps.length ?? 0;
  }

  goBack(): void {
    this.location.back();
  }

  goToReminders(): void {
    this.router.navigate(['/reminders']);
  }

  stepInstruction(step: ReminderResponse): string {
    return step.instruction ?? step.careType;
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
    this.reminderService.markCareDone(step.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.loadPlan(),
        error: () => {
          this.markingDoneId = null;
        },
      });
  }

  private loadPlan(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.treatmentPlanService.getTreatmentPlan(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plan = res.data;
          this.state = 'ready';
          this.markingDoneId = null;
        },
        error: () => {
          this.state = 'error';
        },
      });
  }
}
