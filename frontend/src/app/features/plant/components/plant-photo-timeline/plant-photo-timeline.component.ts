import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { IdentificationService } from '../../../identification/services/identification.service';
import { IdentificationResponse } from '../../../identification/models/identification.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

@Component({
  selector: 'app-plant-photo-timeline',
  templateUrl: './plant-photo-timeline.component.html',
  styleUrls: ['./plant-photo-timeline.component.scss'],
})
export class PlantPhotoTimelineComponent implements OnInit, OnDestroy {
  @Input() plantId!: number;
  @Input() selectedScanId: number | null = null;
  @Output() readonly scanSelected = new EventEmitter<IdentificationResponse>();

  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly skeletonItems = [1, 2, 3, 4];

  scans: IdentificationResponse[] = [];
  loading = true;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly identificationService: IdentificationService) {}

  ngOnInit(): void {
    this.loadScans();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Re-fetches the strip — call after a new scan has been submitted for this plant. */
  reload(): void {
    this.loadScans();
  }

  select(scan: IdentificationResponse): void {
    this.scanSelected.emit(scan);
  }

  healthClass(scan: IdentificationResponse): string {
    if (scan.healthStatus === 'HEALTHY') return 'health-healthy';
    if (scan.healthStatus === 'ISSUES_DETECTED') return 'health-issues';
    return 'health-unknown';
  }

  private loadScans(): void {
    this.loading = true;
    this.identificationService.getPlantIdentifications(this.plantId, 0, 20)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.scans = [...res.data.content].reverse();
          this.loading = false;
          const newest = this.scans[this.scans.length - 1];
          if (newest && this.selectedScanId === null) {
            this.scanSelected.emit(newest);
          }
        },
        error: () => {
          this.loading = false;
        },
      });
  }
}
