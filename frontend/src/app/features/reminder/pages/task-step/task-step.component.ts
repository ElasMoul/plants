import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TreatmentPlanService } from '../../services/treatment-plan.service';
import { ReminderService } from '../../services/reminder.service';
import { ReminderResponse } from '../../models/reminder.model';
import { parseDetailAsList, ParsedDetail } from '../../../../shared/utils/detail-list.util';

type PageState = 'loading' | 'ready' | 'error';

@Component({
    selector: 'app-task-step',
    templateUrl: './task-step.component.html',
    styleUrls: ['./task-step.component.scss'],
    standalone: false
})
export class TaskStepComponent implements OnInit, OnDestroy {
  state: PageState = 'loading';
  step: ReminderResponse | null = null;
  detailList: ParsedDetail | null = null;
  markingDone = false;

  private planId!: number;
  private stepId!: number;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly location: Location,
    private readonly router: Router,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly reminderService: ReminderService,
  ) {}

  ngOnInit(): void {
    this.planId = Number(this.route.snapshot.paramMap.get('planId'));
    this.stepId = Number(this.route.snapshot.paramMap.get('stepId'));
    this.loadStep();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  goBack(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (returnUrl) {
      this.router.navigateByUrl(returnUrl);
    } else {
      this.location.back();
    }
  }

  markDone(): void {
    if (!this.step || this.markingDone || !this.step.enabled) return;
    this.markingDone = true;
    this.reminderService.markCareDone(this.stepId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.markingDone = false;
          this.goBack();
        },
        error: (err: { status: number }) => {
          this.markingDone = false;
          if (err.status === 400) {
            this.goBack();
          }
        },
      });
  }

  private loadStep(): void {
    this.treatmentPlanService.getTreatmentPlan(this.planId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const found = res.data.steps.find(s => s.id === this.stepId) ?? null;
          if (!found) {
            this.state = 'error';
            return;
          }
          this.step = found;
          this.detailList = parseDetailAsList(found.stepDetail);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }
}
