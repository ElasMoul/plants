import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SpeciesService } from '../../services/species.service';
import { SpeciesSummaryDto } from '../../models/species.model';
import { PageResponse } from '../../../../core/models/api-response.model';
import { IdentificationService } from '../../../identification/services/identification.service';
import { AnalyzeEmitPayload } from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';

type SpeciesFilter = 'all' | 'issues' | 'recent';

@Component({
  selector: 'app-species-list',
  templateUrl: './species-list.component.html',
  styleUrls: ['./species-list.component.scss'],
})
export class SpeciesListComponent implements OnInit, OnDestroy {
  species: SpeciesSummaryDto[] = [];
  page: PageResponse<SpeciesSummaryDto> | null = null;
  loading = true;
  currentPage = 0;
  pageSize = 20;
  skeletons = Array(6);
  activeFilter: SpeciesFilter = 'all';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly speciesService: SpeciesService,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.loadSpecies();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Filters only the currently-fetched page — no backend query param for this yet, so the
  // "With Issues"/"Recently scanned" counts are scoped to one page at a time, not the user's
  // full species list.
  get filteredSpecies(): SpeciesSummaryDto[] {
    if (this.activeFilter === 'issues') {
      return this.species.filter(s => s.healthSummary !== 'All healthy');
    }
    if (this.activeFilter === 'recent') {
      return this.species
        .filter((s): s is SpeciesSummaryDto & { lastScanAt: string } => s.lastScanAt !== null)
        .sort((a, b) => new Date(b.lastScanAt).getTime() - new Date(a.lastScanAt).getTime());
    }
    return this.species;
  }

  setFilter(filter: SpeciesFilter): void {
    this.activeFilter = filter;
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

  loadSpecies(): void {
    this.loading = true;
    this.speciesService.getMySpecies(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.page = res.data;
        this.species = res.data?.content ?? [];
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Failed to load your garden. Is the backend running?', 'Dismiss', { duration: 5000 });
        this.loading = false;
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadSpecies();
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
