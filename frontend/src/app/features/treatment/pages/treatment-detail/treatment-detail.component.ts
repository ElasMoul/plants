import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, interval } from 'rxjs';
import { filter, map, switchMap, take, takeUntil, takeWhile, timeout } from 'rxjs/operators';
import { AiErrorService } from '../../../../core/services/ai-error.service';
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

// Same convention as IdentificationService.pollUntilComplete() — 3s interval, bounded timeout.
const DESCRIPTION_POLL_INTERVAL_MS = 3000;
const DESCRIPTION_POLL_TIMEOUT_MS = 30000;

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
  retryingDescription = false;

  private sentinelObserver?: IntersectionObserver;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly treatmentService: TreatmentService,
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly aiErrorService: AiErrorService,
  ) {}

  ngOnInit(): void {
    const section = this.route.snapshot.queryParamMap.get('section') as Section | null;
    if (section === 'overview' || section === 'plan') {
      this.activeSection = section;
    }
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
        error: (err: HttpErrorResponse) => {
          this.craftingPlan = false;
          this.aiErrorService.notify(err);
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
          if (!this.treatment.descriptionStatus || this.treatment.descriptionStatus === 'PENDING') {
            this.pollForDescription(this.treatment.id);
          }
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  retryDescription(): void {
    if (this.retryingDescription || !this.treatment) return;
    this.retryingDescription = true;
    this.treatmentService.regenerateDescription(this.treatment.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.treatment = res.data;
          this.retryingDescription = false;
          this.pollForDescription(this.treatment!.id);
        },
        error: () => {
          this.retryingDescription = false;
        },
      });
  }

  // Polls on descriptionStatus (T9.B) — stops when status leaves PENDING (READY or FAILED).
  private pollForDescription(treatmentId: number): void {
    interval(DESCRIPTION_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.treatmentService.getTreatment(treatmentId)),
      map(res => res.data),
      takeWhile(t => !t.descriptionStatus || t.descriptionStatus === 'PENDING', true),
      filter(t => t.descriptionStatus !== 'PENDING'),
      take(1),
      timeout(DESCRIPTION_POLL_TIMEOUT_MS),
      takeUntil(this.destroy$),
    ).subscribe({
      next: t => { this.treatment = t; },
      error: () => { /* timed out — backend will eventually set FAILED; user can retry */ },
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
