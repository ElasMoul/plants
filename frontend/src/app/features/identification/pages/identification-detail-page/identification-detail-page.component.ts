import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentificationService } from '../../services/identification.service';
import {
  IdentificationResponse,
  SavePreviewEditEvent,
  SpeciesMatchDto,
} from '../../models/identification.model';
import { PlantResponse } from '../../../plant/models/plant.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';
import { AiErrorService } from '../../../../core/services/ai-error.service';

type DetailState =
  | 'loading'
  | 'pending'
  | 'species-confirm'
  | 'plant-select'
  | 'ready'
  | 'failed'
  | 'blocked'
  | 'not-found';

// Backend failureReason tag (IdentificationServiceImpl.classifyFailureReason) for a gateway 402 —
// the AI ceiling was hit, so the poller must terminate into the explicit block state, not a
// generic "we couldn't identify this" failure (platform D023).
const BLOCKED_FAILURE_REASON = 'AI_LIMIT_REACHED';

@Component({
    selector: 'app-identification-detail-page',
    templateUrl: './identification-detail-page.component.html',
    styleUrls: ['./identification-detail-page.component.scss'],
    standalone: false
})
export class IdentificationDetailPageComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  state: DetailState = 'loading';
  result: IdentificationResponse | null = null;
  isRetrying = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
    private readonly aiErrorService: AiErrorService,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadIdentification(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSaved(plant: PlantResponse): void {
    this.snackBar.open('Added to your garden!', undefined, { duration: 3000 });
    this.router.navigate(['/plants', plant.id]);
  }

  onSpeciesResolved(match: SpeciesMatchDto): void {
    if (this.result) {
      this.result = { ...this.result, speciesId: match.speciesId };
    }
    this.state = 'plant-select';
  }

  onPlantResolved(identification: IdentificationResponse): void {
    this.snackBar.open('Added to your garden!', undefined, { duration: 3000 });
    this.router.navigate(['/plants', identification.plantId]);
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

  onDiscard(): void {
    this.router.navigate(['/identify']);
  }

  onRetry(): void {
    if (!this.result || this.isRetrying) return;
    this.isRetrying = true;
    const id = this.result.id;
    this.identificationService.retryIdentification(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.isRetrying = false;
          this.state = 'pending';
          this.pollForCompletion(id);
        },
        error: (err: HttpErrorResponse) => {
          this.isRetrying = false;
          this.aiErrorService.notify(err);
        },
      });
  }

  private loadIdentification(id: number): void {
    this.identificationService.getById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => this.handleResult(res.data),
        error: () => { this.state = 'not-found'; },
      });
  }

  private handleResult(data: IdentificationResponse): void {
    this.result = data;

    // If already in a resolved state, just update the data in place — enrichment stage resolved.
    if (this.state !== 'loading' && this.state !== 'pending') {
      return;
    }

    if (data.status === 'PENDING') {
      this.state = 'pending';
      this.pollForCompletion(data.id);
      return;
    }
    if (data.status === 'FAILED') {
      this.state = data.failureReason === BLOCKED_FAILURE_REASON ? 'blocked' : 'failed';
      return;
    }
    if (data.plantId != null) {
      this.router.navigate(['/plants', data.plantId]);
      return;
    }
    // Flow 2/3 (species/plant page scans) already know their speciesId at submission time —
    // skip species-confirm entirely and go straight to the existing "add to garden" preview.
    // Flow 1 (Garden FAB, entry point unknown) has no speciesId yet and needs to confirm it.
    this.state = data.speciesId != null ? 'ready' : 'species-confirm';
  }

  private pollForCompletion(id: number): void {
    this.identificationService.pollUntilComplete(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => this.handleResult(result),
        error: () => { this.state = 'failed'; },
      });
  }
}
