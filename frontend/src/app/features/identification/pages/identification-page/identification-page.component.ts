import { Component, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import {
  AnalyzeEmitPayload,
  AnnotationRegion,
  IdentificationResponse,
  SavePreviewEditEvent,
} from '../../models/identification.model';
import { PlantResponse } from '../../../plant/models/plant.model';

type PageState = 'idle' | 'analyzing' | 'preview' | 'error';

@Component({
  selector: 'app-identification-page',
  templateUrl: './identification-page.component.html',
  styleUrls: ['./identification-page.component.scss'],
})
export class IdentificationPageComponent implements OnDestroy {
  state: PageState = 'idle';
  result: IdentificationResponse | null = null;
  errorMessage = '';
  selectedRegionIndex: number | null = null;
  selectedRegion: AnnotationRegion | null = null;

  private lastPayload: AnalyzeEmitPayload | null = null;

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

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
    this.selectedRegion = index !== null && this.result?.annotationRegions
      ? (this.result.annotationRegions[index] ?? null)
      : null;
  }

  onAnalyze(payload: AnalyzeEmitPayload): void {
    this.lastPayload = payload;
    this.state = 'analyzing';
    this.result = null;
    this.errorMessage = '';
    this.selectedRegionIndex = null;
    this.selectedRegion = null;

    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.result = res.data;
          this.state = 'preview';
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage = this.mapError(err);
          this.state = 'error';
        },
      });
  }

  onSaved(plant: PlantResponse): void {
    this.snackBar.open('Added to your garden!', undefined, { duration: 3000 });
    this.router.navigate(['/plants', plant.id]);
  }

  onEditBeforeSave(event: SavePreviewEditEvent): void {
    if (!this.result) return;
    this.router.navigate(['/plants/new'], {
      queryParams: {
        species:    this.result.scientificName,
        commonName: this.result.commonName,
        nickname:   event.nickname,
        location:   event.location || undefined,
      },
    });
  }

  retry(): void {
    if (this.lastPayload) {
      this.onAnalyze(this.lastPayload);
    }
  }

  onDiscard(): void {
    this.reset();
  }

  reset(): void {
    this.state = 'idle';
    this.result = null;
    this.errorMessage = '';
    this.selectedRegionIndex = null;
    this.selectedRegion = null;
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
