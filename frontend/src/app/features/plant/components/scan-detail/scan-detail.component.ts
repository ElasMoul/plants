import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { IdentificationService } from '../../../identification/services/identification.service';
import { TreatmentService } from '../../services/treatment.service';
import { IdentificationResponse } from '../../../identification/models/identification.model';
import { TreatmentResponse } from '../../models/treatment.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

type PageState = 'loading' | 'ready' | 'error';

@Component({
    selector: 'app-scan-detail',
    templateUrl: './scan-detail.component.html',
    styleUrls: ['./scan-detail.component.scss'],
    standalone: false
})
export class ScanDetailComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;

  state: PageState = 'loading';
  scan: IdentificationResponse | null = null;
  activeTreatments: TreatmentResponse[] = [];
  selectedRegionIndex: number | null = null;

  plantId!: number;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly identificationService: IdentificationService,
    private readonly treatmentService: TreatmentService,
  ) {}

  ngOnInit(): void {
    this.plantId = Number(this.route.snapshot.paramMap.get('id'));
    const scanId = Number(this.route.snapshot.paramMap.get('scanId'));

    this.identificationService.getById(scanId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.scan = res.data;
          this.state = 'ready';
          this.loadActiveTreatments();
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  goBack(): void {
    this.router.navigate(['/plants', this.plantId], { queryParams: { section: 'scans' } });
  }

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
  }

  get healthLabel(): string {
    switch (this.scan?.healthStatus) {
      case 'HEALTHY': return 'Healthy';
      case 'ISSUES_DETECTED': return 'Issues detected';
      default: return 'Unknown';
    }
  }

  get healthClass(): string {
    switch (this.scan?.healthStatus) {
      case 'HEALTHY': return 'health-healthy';
      case 'ISSUES_DETECTED': return 'health-issues';
      default: return 'health-unknown';
    }
  }

  private loadActiveTreatments(): void {
    this.treatmentService.getActiveTreatments(this.plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => { this.activeTreatments = res.data; },
        error: () => { this.activeTreatments = []; },
      });
  }
}
