import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AiErrorService } from '../../../../core/services/ai-error.service';
import { IdentificationService } from '../../services/identification.service';
import {
  ConfidenceLevel,
  PlantNetCandidateDto,
  PlantNetReferenceImageDto,
  SpeciesMatchDto,
  getConfidenceLevel,
} from '../../models/identification.model';

@Component({
    selector: 'app-species-confirm-step',
    templateUrl: './species-confirm-step.component.html',
    styleUrls: ['./species-confirm-step.component.scss'],
    standalone: false
})
export class SpeciesConfirmStepComponent implements OnInit, OnDestroy {
  @Input() identificationId!: number;
  @Output() readonly resolved = new EventEmitter<SpeciesMatchDto>();

  loading = true;
  submitting = false;
  match: SpeciesMatchDto | null = null;
  // For auto-confirmable: start collapsed on the single top card; expand on "Not this one"
  showFullList = false;

  readonly brokenImages = new Set<string>();

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly identificationService: IdentificationService,
    private readonly router: Router,
    private readonly aiErrorService: AiErrorService,
  ) {}

  ngOnInit(): void {
    this.identificationService.getSpeciesMatch(this.identificationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: match => {
          this.match = match;
          this.loading = false;
        },
        error: (err: HttpErrorResponse) => {
          this.aiErrorService.notify(err);
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get hasCandidates(): boolean {
    return !!(this.match?.candidates?.length);
  }

  get displayedCandidates(): PlantNetCandidateDto[] {
    const candidates = this.match?.candidates;
    if (!candidates?.length) return [];
    if (this.match?.autoConfirmable && !this.showFullList) {
      return [candidates[0]];
    }
    return candidates;
  }

  get plantNetBadgeLabel(): string | null {
    const version = this.match?.plantNetVersion;
    return version ? `Pl@ntNet ${version}` : (this.hasCandidates ? 'Pl@ntNet' : null);
  }

  confidenceLevel(score: number): ConfidenceLevel {
    return getConfidenceLevel(score);
  }

  primaryCommonName(candidate: PlantNetCandidateDto): string {
    return candidate.commonNames?.[0] ?? '';
  }

  validImages(candidate: PlantNetCandidateDto): PlantNetReferenceImageDto[] {
    return (candidate.referenceImages ?? [])
      .filter(img => img.smallUrl && !this.brokenImages.has(img.smallUrl))
      .slice(0, 3);
  }

  onImageError(url: string): void {
    this.brokenImages.add(url);
  }

  onConfirmCandidate(candidate: PlantNetCandidateDto): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService
      .resolveSpecies(this.identificationId, true, candidate.scientificName)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.submitting = false;
          this.resolved.emit(result);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting = false;
          this.aiErrorService.notify(err);
        },
      });
  }

  onShowFullList(): void {
    this.showFullList = true;
  }

  onRescan(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService
      .resolveSpecies(this.identificationId, false)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.router.navigate(['/identify']),
        error: () => this.router.navigate(['/identify']),
      });
  }

  // Legacy path: single species confirmed by a non-PlantNet vision model (yes/no)
  onConfirmLegacy(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.identificationService
      .resolveSpecies(this.identificationId, true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.submitting = false;
          this.resolved.emit(result);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting = false;
          this.aiErrorService.notify(err);
        },
      });
  }
}
