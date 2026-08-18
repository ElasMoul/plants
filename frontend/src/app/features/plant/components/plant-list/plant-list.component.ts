import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { PlantService } from '../../services/plant.service';
import { PlantResponse } from '../../models/plant.model';
import { PageResponse } from '@plantpal/shared-core';
import { IdentificationService } from '../../../identification/services/identification.service';
import { AnalyzeEmitPayload } from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';

@Component({
    selector: 'app-plant-list',
    templateUrl: './plant-list.component.html',
    styleUrls: ['./plant-list.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class PlantListComponent implements OnInit, OnDestroy {
  plants: PlantResponse[] = [];
  page: PageResponse<PlantResponse> | null = null;
  loading = true;
  currentPage = 0;
  pageSize = 20;
  skeletons = Array(6);

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadPlants();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openIdentifyDialog(): void {
    const dialogRef = this.dialog.open(IdentificationUploadDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: false,
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((payload?: AnalyzeEmitPayload) => {
        if (payload) {
          this.submitIdentification(payload);
        }
      });
  }

  loadPlants(): void {
    this.loading = true;
    this.plantService.getPlants(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.page = res.data;
        this.plants = res.data?.content ?? [];
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.snackBar.open('Failed to load plants. Is the backend running?', 'Dismiss', { duration: 5000 });
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  trackByPlantId(_index: number, plant: PlantResponse): number {
    return plant.id;
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPlants();
  }

  onArchive(id: number): void {
    this.plantService.archivePlant(id).subscribe({
      next: () => {
        this.snackBar.open('Plant archived.', 'Undo', { duration: 4000 });
        this.loadPlants();
      },
      error: () => {
        this.snackBar.open('Could not archive plant.', 'Dismiss', { duration: 4000 });
      },
    });
  }

  private submitIdentification(payload: AnalyzeEmitPayload): void {
    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId)
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
