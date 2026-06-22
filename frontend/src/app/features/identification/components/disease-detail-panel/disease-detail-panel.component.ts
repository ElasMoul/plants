import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AiErrorService } from '../../../../core/services/ai-error.service';
import { ActionPlanDto, AnnotationRegion, CarePlanDto } from '../../models/identification.model';
import { IdentificationService } from '../../services/identification.service';

interface CureCacheEntry {
  advice: string;
  actionPlan: ActionPlanDto | null;
  addedToPlan: boolean;
  reasoningModelUsed: string | null;
}

// Cure-advice display + "Add to care plan" only — this panel deliberately does NOT offer its
// own "Start treatment plan" action. That used to call TreatmentPlanService.createFromActionPlan()
// directly, bypassing the Treatment entity's duplicate-protection (see ARCHITECT.md's "Two
// Treatment concepts"); a user could end up starting two treatment plans for the same disease by
// using this button and the PEST care-card's button separately. CareCardComponent is now the
// single place a treatment is started from, via the protected TreatmentService path.
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
  reasoningModelUsed: string | null = null;
  loadingAdvice = false;
  adviceError = false;
  addingToPlan = false;
  addedToPlan = false;

  // Keyed by identificationId:regionLabel — re-selecting a region already asked about should
  // show the cached advice instead of presenting "Ask for cure" again.
  private readonly cureCache = new Map<string, CureCacheEntry>();

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly aiErrorService: AiErrorService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['region'] && !changes['identificationId']) return;

    this.loadingAdvice = false;
    this.adviceError = false;
    this.addingToPlan = false;

    const key = this.cacheKey();
    const cached = key ? this.cureCache.get(key) : undefined;
    if (cached) {
      this.advice = cached.advice;
      this.actionPlan = cached.actionPlan;
      this.addedToPlan = cached.addedToPlan;
      this.reasoningModelUsed = cached.reasoningModelUsed;
    } else {
      this.advice = null;
      this.actionPlan = null;
      this.addedToPlan = false;
      this.reasoningModelUsed = null;
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
      reasoningModelUsed: this.reasoningModelUsed,
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isDisease(): boolean {
    return this.region?.type === 'DISEASE';
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
          this.reasoningModelUsed = result.reasoningModelUsed ?? null;
          this.loadingAdvice = false;
          this.cacheCurrent();
        },
        error: (err: HttpErrorResponse) => {
          this.adviceError = true;
          this.loadingAdvice = false;
          this.aiErrorService.notify(err);
        },
      });
  }

  addToCarePlan(): void {
    if (!this.region || !this.advice || this.addingToPlan || this.addedToPlan) return;
    this.addingToPlan = true;

    this.identificationService
      .addCareCard(
        this.identificationId,
        this.region.label,
        this.advice,
        this.actionPlan,
        this.reasoningModelUsed,
      )
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
}
