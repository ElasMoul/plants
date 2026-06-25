import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, interval } from 'rxjs';
import { filter, map, switchMap, take, takeUntil, takeWhile, timeout } from 'rxjs/operators';
import { SpeciesService } from '../../services/species.service';
import { SpeciesResponse } from '../../models/species.model';
import { PlantService } from '../../../plant/services/plant.service';
import { PlantResponse } from '../../../plant/models/plant.model';
import { IdentificationService } from '../../../identification/services/identification.service';
import { AnalyzeEmitPayload } from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

const DESCRIPTION_POLL_INTERVAL_MS = 3000;
const DESCRIPTION_POLL_TIMEOUT_MS = 32000;

@Component({
  selector: 'app-species-detail',
  templateUrl: './species-detail.component.html',
  styleUrls: ['./species-detail.component.scss'],
})
export class SpeciesDetailComponent implements OnInit, OnDestroy {
  species: SpeciesResponse | null = null;
  plants: PlantResponse[] = [];
  loading = true;
  plantsLoading = true;
  retrying = false;

  readonly placeholderImage = PLACEHOLDER_IMAGE;

  private speciesId!: number;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly speciesService: SpeciesService,
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.speciesId = Number(this.route.snapshot.paramMap.get('id'));

    this.speciesService.getSpecies(this.speciesId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.species = res.data;
          this.loading = false;
          if (this.species.descriptionStatus === 'PENDING') {
            this.pollForDescription(this.speciesId);
          }
        },
        error: () => {
          this.snackBar.open('Species not found.', 'Dismiss', { duration: 4000 });
          this.loading = false;
        },
      });

    this.plantService.getPlants(0, 50, this.speciesId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.plants = res.data?.content ?? [];
          this.plantsLoading = false;
        },
        error: () => {
          this.plants = [];
          this.plantsLoading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  retryDescription(): void {
    if (this.retrying || !this.species) return;
    this.retrying = true;
    this.speciesService.regenerateDescription(this.speciesId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.species = res.data;
          this.retrying = false;
          this.pollForDescription(this.speciesId);
        },
        error: () => {
          this.retrying = false;
          this.snackBar.open('Could not retry description generation.', 'Dismiss', { duration: 4000 });
        },
      });
  }

  openAddPlantDialog(): void {
    if (!this.species) return;
    const dialogRef = this.dialog.open(IdentificationUploadDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: false,
      data: { speciesId: this.species.id, speciesName: this.species.scientificName },
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((payload?: AnalyzeEmitPayload) => {
        if (payload) {
          this.submitIdentification(payload);
        }
      });
  }

  private pollForDescription(speciesId: number): void {
    interval(DESCRIPTION_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.speciesService.getSpecies(speciesId)),
      map(res => res.data),
      takeWhile(s => s.descriptionStatus === 'PENDING', true),
      filter(s => s.descriptionStatus !== 'PENDING'),
      take(1),
      timeout(DESCRIPTION_POLL_TIMEOUT_MS),
      takeUntil(this.destroy$),
    ).subscribe({
      next: s => { this.species = s; },
      error: () => { /* timed out — leave current state; user can retry */ },
    });
  }

  private submitIdentification(payload: AnalyzeEmitPayload): void {
    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId, payload.speciesId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open(
            "Identification started — view it in the Identify tab.",
            undefined,
            { duration: 3000 },
          );
          this.router.navigate(['/identify']);
        },
        error: (err: HttpErrorResponse) => {
          const message = err.status === 0
            ? 'Connection problem — check your internet and try again'
            : 'Could not start identification. Please try again.';
          this.snackBar.open(message, 'Dismiss', { duration: 5000 });
        },
      });
  }
}
