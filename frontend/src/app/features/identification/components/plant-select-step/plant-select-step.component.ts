import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import { IdentificationResponse, PlantSummaryDto } from '../../models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

@Component({
  selector: 'app-plant-select-step',
  templateUrl: './plant-select-step.component.html',
  styleUrls: ['./plant-select-step.component.scss'],
})
export class PlantSelectStepComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;

  @Input() identificationId!: number;
  @Output() readonly resolved = new EventEmitter<IdentificationResponse>();

  loading = true;
  submitting = false;
  candidatePlants: PlantSummaryDto[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.identificationService.getPlantMatch(this.identificationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: match => {
          this.candidatePlants = match.candidatePlants;
          this.loading = false;
          // No candidates — nothing for the user to choose between, skip straight to creation.
          if (this.candidatePlants.length === 0) {
            this.resolvePlant(null);
          }
        },
        error: () => {
          this.snackBar.open('Could not load your plants — please try again.', 'Dismiss', { duration: 4000 });
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSelectPlant(plantId: number): void {
    this.resolvePlant(plantId);
  }

  onCreateNewPlant(): void {
    this.resolvePlant(null);
  }

  private resolvePlant(plantId: number | null): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService.resolvePlant(this.identificationId, plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.submitting = false;
          this.resolved.emit(result);
        },
        error: () => {
          this.submitting = false;
          this.snackBar.open('Could not save this plant — please try again.', 'Dismiss', { duration: 4000 });
        },
      });
  }
}
