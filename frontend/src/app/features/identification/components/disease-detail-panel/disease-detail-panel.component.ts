import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ActionPlanDto, AnnotationRegion, CarePlanDto } from '../../models/identification.model';
import { IdentificationService } from '../../services/identification.service';
import { TreatmentPlanService } from '../../../reminder/services/treatment-plan.service';

interface CureCacheEntry {
  advice: string;
  actionPlan: ActionPlanDto | null;
  addedToPlan: boolean;
  treatmentStarted: boolean;
}

@Component({
  selector: 'app-disease-detail-panel',
  templateUrl: './disease-detail-panel.component.html',
  styleUrls: ['./disease-detail-panel.component.scss'],
})
export class DiseaseDetailPanelComponent implements OnChanges, OnDestroy {
  @Input() region: AnnotationRegion | null = null;
  @Input() species: string | null = null;
  @Input() identificationId!: number;
  @Input() plantId: number | null = null;
  @Output() readonly carePlanUpdated = new EventEmitter<CarePlanDto>();

  advice: string | null = null;
  actionPlan: ActionPlanDto | null = null;
  loadingAdvice = false;
  adviceError = false;
  addingToPlan = false;
  addedToPlan = false;
  startingTreatment = false;
  treatmentStarted = false;

  // Keyed by identificationId:regionLabel — re-selecting a region already asked about should
  // show the cached advice instead of presenting "Ask for cure" again.
  private readonly cureCache = new Map<string, CureCacheEntry>();

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['region'] && !changes['identificationId']) return;

    this.loadingAdvice = false;
    this.adviceError = false;
    this.addingToPlan = false;
    this.startingTreatment = false;

    const key = this.cacheKey();
    const cached = key ? this.cureCache.get(key) : undefined;
    if (cached) {
      this.advice = cached.advice;
      this.actionPlan = cached.actionPlan;
      this.addedToPlan = cached.addedToPlan;
      this.treatmentStarted = cached.treatmentStarted;
    } else {
      this.advice = null;
      this.actionPlan = null;
      this.addedToPlan = false;
      this.treatmentStarted = false;
    }
  }

  private cacheKey(): string | null {
    return this.region ? `${this.identificationId}:${this.region.label}` : null;
  }

  private cacheCurrent(): void {
    const key = this.cacheKey();
    if (!key || !this.advice) return;
    this.cureCache.set(key, {
      advice: this.advice,
      actionPlan: this.actionPlan,
      addedToPlan: this.addedToPlan,
      treatmentStarted: this.treatmentStarted,
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isDisease(): boolean {
    return this.region?.type === 'DISEASE';
  }

  get showTreatmentOption(): boolean {
    return this.actionPlan?.type === 'TREATMENT' && this.plantId !== null;
  }

  askForCure(): void {
    if (!this.region || !this.species) return;
    this.loadingAdvice = true;
    this.adviceError = false;

    this.identificationService
      .getCureAdvice(this.identificationId, this.region.label, this.species)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.advice = result.advice;
          this.actionPlan = result.actionPlan;
          this.loadingAdvice = false;
          this.cacheCurrent();
        },
        error: () => {
          this.adviceError = true;
          this.loadingAdvice = false;
        },
      });
  }

  addToCarePlan(): void {
    if (!this.region || !this.advice || this.addingToPlan || this.addedToPlan) return;
    this.addingToPlan = true;

    this.identificationService
      .addCareCard(this.identificationId, this.region.label, this.advice, this.actionPlan)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: plan => {
          this.addingToPlan = false;
          this.addedToPlan = true;
          this.cacheCurrent();
          this.carePlanUpdated.emit(plan);
        },
        error: () => {
          this.addingToPlan = false;
        },
      });
  }

  startTreatmentPlan(): void {
    const plantId = this.plantId;
    if (!this.region || !this.actionPlan || plantId === null) return;
    if (this.startingTreatment || this.treatmentStarted) return;

    this.startingTreatment = true;
    this.treatmentPlanService.createFromActionPlan({
      plantId,
      title: this.region.label,
      sourceCareCardType: 'PEST',
      actionPlan: this.actionPlan,
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.startingTreatment = false;
          this.treatmentStarted = true;
          this.cacheCurrent();
          const snackRef = this.snackBar.open('Treatment plan started', 'View', { duration: 5000 });
          snackRef.onAction()
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
              this.router.navigate(['/treatment-plans', res.data.id]);
            });
        },
        error: () => {
          this.startingTreatment = false;
          this.snackBar.open('Could not start treatment plan.', 'Dismiss', { duration: 4000 });
        },
      });
  }
}
