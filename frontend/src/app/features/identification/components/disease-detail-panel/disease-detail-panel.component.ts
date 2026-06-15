import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AnnotationRegion } from '../../models/identification.model';
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

  advice: string | null = null;
  loadingAdvice = false;
  adviceError = false;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly identificationService: IdentificationService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['region']) {
      this.advice = null;
      this.loadingAdvice = false;
      this.adviceError = false;
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
}
