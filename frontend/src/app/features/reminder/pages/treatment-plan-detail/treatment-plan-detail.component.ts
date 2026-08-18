import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TreatmentPlanService } from '../../services/treatment-plan.service';
import { TreatmentPlanResponse } from '../../models/treatment-plan.model';

type PageState = 'loading' | 'ready' | 'error';

@Component({
    selector: 'app-treatment-plan-detail',
    templateUrl: './treatment-plan-detail.component.html',
    styleUrls: ['./treatment-plan-detail.component.scss'],
    standalone: false
})
export class TreatmentPlanDetailComponent implements OnInit, OnDestroy {
  readonly skeletonSteps = [1, 2, 3];

  state: PageState = 'loading';
  plan: TreatmentPlanResponse | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location,
    private readonly treatmentPlanService: TreatmentPlanService,
  ) {}

  ngOnInit(): void {
    this.loadPlan();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  goBack(): void {
    this.location.back();
  }

  goToReminders(): void {
    this.router.navigate(['/reminders']);
  }

  loadPlan(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.treatmentPlanService.getTreatmentPlan(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plan = res.data;
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }
}
