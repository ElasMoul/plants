import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TreatmentService } from '../../../plant/services/treatment.service';
import { TreatmentResponse } from '../../../plant/models/treatment.model';
import { PlantService } from '../../../plant/services/plant.service';
import { PlantResponse } from '../../../plant/models/plant.model';
import { IdentificationService } from '../../../identification/services/identification.service';
import { TreatmentPlanService } from '../../../reminder/services/treatment-plan.service';
import { TreatmentPlanResponse } from '../../../reminder/models/treatment-plan.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

type PageState = 'loading' | 'ready' | 'error';
type Section = 'overview' | 'plan';

@Component({
  selector: 'app-treatment-detail',
  templateUrl: './treatment-detail.component.html',
  styleUrls: ['./treatment-detail.component.scss'],
})
export class TreatmentDetailComponent implements OnInit, OnDestroy {
  // Same IntersectionObserver-driven collapse pattern as plant-detail.component.ts (T6.10) —
  // reused rather than reinventing a second sticky-header mechanism.
  @ViewChild('scrollSentinel') set scrollSentinel(el: ElementRef<HTMLDivElement> | undefined) {
    if (el && !this.sentinelObserver) {
      this.sentinelObserver = new IntersectionObserver(
        ([entry]) => {
          this.headerCollapsed = !entry.isIntersecting;
        },
        // rootMargin pushes the effective viewport top down 40px, so the sentinel
        // (sitting at scrollY=0) only counts as "out of view" — i.e. collapse fires —
        // once the user has scrolled past 40px.
        { threshold: 0, rootMargin: '-40px 0px 0px 0px' },
      );
      this.sentinelObserver.observe(el.nativeElement);
    }
  }

  readonly placeholderImage = PLACEHOLDER_IMAGE;

  state: PageState = 'loading';
  headerCollapsed = false;
  activeSection: Section = 'overview';

  treatment: TreatmentResponse | null = null;
  plant: PlantResponse | null = null;
  scanPhotoUrl: string | null = null;

  plan: TreatmentPlanResponse | null = null;
  planLoading = false;
  craftingPlan = false;

  private sentinelObserver?: IntersectionObserver;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
    private readonly treatmentService: TreatmentService,
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly treatmentPlanService: TreatmentPlanService,
  ) {}

  ngOnInit(): void {
    this.loadTreatment();
  }

  ngOnDestroy(): void {
    this.sentinelObserver?.disconnect();
    this.destroy$.next();
    this.destroy$.complete();
  }

  selectSection(section: Section): void {
    this.activeSection = section;
    if (section === 'plan' && this.treatment?.treatmentPlanId && !this.plan) {
      this.loadPlan(this.treatment.treatmentPlanId);
    }
  }

  goToPlant(): void {
    if (this.treatment) {
      this.router.navigate(['/plants', this.treatment.plantId]);
    }
  }

  craftPlan(): void {
    if (!this.treatment || this.craftingPlan) return;
    this.craftingPlan = true;
    this.treatmentService.craftPlan(this.treatment.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.craftingPlan = false;
          this.treatment = res.data;
          if (this.treatment.treatmentPlanId) {
            this.loadPlan(this.treatment.treatmentPlanId);
          }
          this.activeSection = 'plan';
        },
        error: () => {
          this.craftingPlan = false;
          this.snackBar.open('Could not craft the treatment plan. Please try again.', 'Dismiss', { duration: 5000 });
        },
      });
  }

  onPlanStepCompleted(): void {
    if (!this.treatment?.treatmentPlanId) return;
    this.treatmentPlanService.getTreatmentPlan(this.treatment.treatmentPlanId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plan = res.data;
          // The backend has no automatic sync between TreatmentPlan completion and the
          // Treatment's own status — flip it here once every step is done.
          if (this.plan.status === 'COMPLETED' && this.treatment && this.treatment.status !== 'COMPLETED') {
            this.completeTreatment();
          }
        },
      });
  }

  private completeTreatment(): void {
    if (!this.treatment) return;
    this.treatmentService.completeTreatment(this.treatment.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.treatment = res.data;
        },
      });
  }

  private loadTreatment(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.treatmentService.getTreatment(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.treatment = res.data;
          this.state = 'ready';
          this.loadPlant(this.treatment.plantId);
          if (this.treatment.identificationId) {
            this.loadScanPhoto(this.treatment.identificationId);
          }
          if (this.treatment.treatmentPlanId) {
            this.loadPlan(this.treatment.treatmentPlanId);
          }
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private loadPlant(plantId: number): void {
    this.plantService.getPlant(plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plant = res.data;
        },
      });
  }

  private loadScanPhoto(identificationId: number): void {
    this.identificationService.getById(identificationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.scanPhotoUrl = res.data.photoUrl;
        },
      });
  }

  private loadPlan(treatmentPlanId: number): void {
    this.planLoading = true;
    this.treatmentPlanService.getTreatmentPlan(treatmentPlanId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plan = res.data;
          this.planLoading = false;
        },
        error: () => {
          this.planLoading = false;
        },
      });
  }
}
