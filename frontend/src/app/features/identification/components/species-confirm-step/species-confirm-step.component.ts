import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import { SpeciesMatchDto } from '../../models/identification.model';

@Component({
  selector: 'app-species-confirm-step',
  templateUrl: './species-confirm-step.component.html',
  styleUrls: ['./species-confirm-step.component.scss'],
})
export class SpeciesConfirmStepComponent implements OnInit, OnDestroy {
  @Input() identificationId!: number;
  @Output() readonly resolved = new EventEmitter<SpeciesMatchDto>();

  loading = true;
  submitting = false;
  match: SpeciesMatchDto | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.identificationService.getSpeciesMatch(this.identificationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: match => {
          this.match = match;
          this.loading = false;
        },
        error: () => {
          this.snackBar.open('Could not check this species — please try again.', 'Dismiss', { duration: 4000 });
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onConfirm(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService.resolveSpecies(this.identificationId, true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.submitting = false;
          this.resolved.emit(result);
        },
        error: () => {
          this.submitting = false;
          this.snackBar.open('Could not save this species — please try again.', 'Dismiss', { duration: 4000 });
        },
      });
  }

  onRescan(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService.resolveSpecies(this.identificationId, false)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.router.navigate(['/identify']),
        error: () => this.router.navigate(['/identify']),
      });
  }
}
