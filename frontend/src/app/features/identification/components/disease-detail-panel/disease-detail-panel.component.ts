import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AnnotationRegion, CarePlanDto } from '../../models/identification.model';
import { IdentificationService } from '../../services/identification.service';

@Component({
  selector: 'app-disease-detail-panel',
  templateUrl: './disease-detail-panel.component.html',
  styleUrls: ['./disease-detail-panel.component.scss'],
})
export class DiseaseDetailPanelComponent implements OnChanges, OnDestroy {
  @Input() region: AnnotationRegion | null = null;
  @Input() species: string | null = null;
  @Input() identificationId!: number;
  @Output() readonly carePlanUpdated = new EventEmitter<CarePlanDto>();

  advice: string | null = null;
  loadingAdvice = false;
  adviceError = false;
  addingToPlan = false;
  addedToPlan = false;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly identificationService: IdentificationService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['region']) {
      this.advice = null;
      this.loadingAdvice = false;
      this.adviceError = false;
      this.addingToPlan = false;
      this.addedToPlan = false;
    }
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
        next: advice => {
          this.advice = advice;
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
      .addCareCard(this.identificationId, this.region.label, this.advice)
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
}
