import { Component, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import {
  AnalyzeEmitPayload,
  IdentificationResponse,
  SaveAsNewEvent,
} from '../../models/identification.model';

type PageState = 'idle' | 'uploading' | 'analyzing' | 'result' | 'error';

@Component({
  selector: 'app-identification-page',
  templateUrl: './identification-page.component.html',
  styleUrls: ['./identification-page.component.scss'],
})
export class IdentificationPageComponent implements OnDestroy {
  state: PageState = 'idle';
  result: IdentificationResponse | null = null;
  errorMessage = '';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.state = 'uploading';
    this.result = null;
    this.errorMessage = '';

    // FormData build is synchronous — move immediately to analyzing state
    this.state = 'analyzing';

    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.result = res.data;
          this.state = 'result';
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage = this.mapError(err);
          this.state = 'error';
        },
      });
  }

  onSaveAsNew(event: SaveAsNewEvent): void {
    this.router.navigate(['/plants/new'], {
      queryParams: { species: event.scientificName, commonName: event.commonName },
    });
  }

  onUpdatePlant(plantId: number): void {
    this.snackBar.open('Plant updated successfully', 'Close', { duration: 3000 });
    this.router.navigate(['/plants', plantId]);
  }

  reset(): void {
    this.state = 'idle';
    this.result = null;
    this.errorMessage = '';
  }

  private mapError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Connection problem — check your internet and try again';
    }
    if (err.status === 404) {
      return 'No matching plant species found — try a clearer photo';
    }
    return 'Something went wrong — please try again';
  }
}
