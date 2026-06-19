import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ActionPlanDto, AnnotationRegion, CarePlanDto } from '../../models/identification.model';
import { IdentificationService } from '../../services/identification.service';
import { TreatmentPlanService } from '../../../reminder/services/treatment-plan.service';

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

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['region']) {
      this.advice = null;
      this.actionPlan = null;
      this.loadingAdvice = false;
      this.adviceError = false;
      this.addingToPlan = false;
      this.addedToPlan = false;
      this.startingTreatment = false;
      this.treatmentStarted = false;
    }
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
