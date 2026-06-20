import { Component, ElementRef, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { HttpErrorResponse } from '@angular/common/http';
import { PlantService } from '../../services/plant.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { ReminderService } from '../../../reminder/services/reminder.service';
import { PlantResponse } from '../../models/plant.model';
import {
  AnalyzeEmitPayload,
  AnnotationRegion,
  CarePlanDto,
  IdentificationResponse,
} from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';
import { CareType } from '../../../reminder/models/reminder.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';
import { PlantPhotoTimelineComponent } from '../plant-photo-timeline/plant-photo-timeline.component';


@Component({
  selector: 'app-plant-detail',
  templateUrl: './plant-detail.component.html',
  styleUrls: ['./plant-detail.component.scss'],
})
export class PlantDetailComponent implements OnInit, OnDestroy {
  @ViewChild(PlantPhotoTimelineComponent) timeline?: PlantPhotoTimelineComponent;

  // Set via the property-binding form below (not ngAfterViewInit) since the sentinel sits
  // behind *ngIf="!loading && plant" and isn't available on the first view-init pass.
  @ViewChild('scrollSentinel') set scrollSentinel(el: ElementRef<HTMLDivElement> | undefined) {
    if (el && !this.sentinelObserver) {
      this.sentinelObserver = new IntersectionObserver(
        ([entry]) => {
          this.headerCollapsed = !entry.isIntersecting;
        },
        { threshold: 0 },
      );
      this.sentinelObserver.observe(el.nativeElement);
    }
  }

  readonly placeholderImage = PLACEHOLDER_IMAGE;
  plant: PlantResponse | null = null;
  loading = true;
  latestCarePlan: CarePlanDto | null = null;
  latestIdentificationId: number | null = null;
  hasIdentification = false;
  carePlanLoaded = false;
  selectedScan: IdentificationResponse | null = null;
  selectedRegionIndex: number | null = null;
  selectedRegion: AnnotationRegion | null = null;
  existingCareTypes: CareType[] = [];

  headerCollapsed = false;
  activeSection: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans' = 'overview';

  private sentinelObserver?: IntersectionObserver;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly plantService: PlantService,
    private readonly identificationService: IdentificationService,
    private readonly reminderService: ReminderService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly bottomSheet: MatBottomSheet,
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));

    this.plantService.getPlant(id).subscribe({
      next: (res) => {
        this.plant = res.data;
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Plant not found.', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/garden']);
      },
    });

    this.identificationService.getPlantIdentifications(id, 0, 1)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const items = res.data.content;
          this.hasIdentification = items.length > 0;
          if (items.length > 0) {
            this.latestCarePlan = items[0].carePlan;
            this.latestIdentificationId = items[0].id;
          }
          this.carePlanLoaded = true;
        },
        error: () => {
          this.carePlanLoaded = true;
        },
      });

    this.reminderService.getReminders()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.existingCareTypes = res.data
            .filter(r => r.plantId === id && r.enabled)
            .map(r => r.careType);
        },
        error: () => {
          this.existingCareTypes = [];
        },
      });
  }

  ngOnDestroy(): void {
    this.sentinelObserver?.disconnect();
    this.destroy$.next();
    this.destroy$.complete();
  }

  selectSection(
    section: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans',
    actionsTemplate?: TemplateRef<unknown>,
  ): void {
    if (section === 'actions') {
      // Real list items land in T6.11 — this placeholder sheet just proves the open/close wiring.
      if (actionsTemplate) {
        this.bottomSheet.open(actionsTemplate);
      }
      return;
    }
    if (section === 'treatment') {
      if (this.plant?.activeTreatmentId) {
        this.router.navigate(['/treatment-plans', this.plant.activeTreatmentId]);
      }
      return;
    }
    this.activeSection = section;
  }

  onScanSelected(scan: IdentificationResponse): void {
    this.selectedScan = scan;
    this.selectedRegionIndex = null;
    this.selectedRegion = null;
  }

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
    this.selectedRegion = index !== null && this.selectedScan?.annotationRegions
      ? (this.selectedScan.annotationRegions[index] ?? null)
      : null;
  }

  onCarePlanUpdated(plan: CarePlanDto): void {
    if (this.selectedScan) {
      this.selectedScan = { ...this.selectedScan, carePlan: plan };
    }
    if (this.selectedScan && this.selectedScan.id === this.latestIdentificationId) {
      this.latestCarePlan = plan;
    }
  }

  openAddScanDialog(): void {
    if (!this.plant) return;
    const dialogRef = this.dialog.open(IdentificationUploadDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: false,
      data: { plantId: this.plant.id, plantNickname: this.plant.nickname },
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((payload?: AnalyzeEmitPayload) => {
        if (payload) {
          this.submitAddScan(payload);
        }
      });
  }

  onArchive(): void {
    if (!this.plant) return;
    this.plantService.archivePlant(this.plant.id).subscribe({
      next: () => {
        this.snackBar.open('Plant archived.', undefined, { duration: 3000 });
        this.router.navigate(['/garden']);
      },
      error: () => {
        this.snackBar.open('Could not archive plant.', 'Dismiss', { duration: 4000 });
      },
    });
  }

  private submitAddScan(payload: AnalyzeEmitPayload): void {
    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId ?? this.plant?.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Scan started — it will appear in the strip shortly.', undefined, {
            duration: 4000,
          });
          this.timeline?.reload();
        },
        error: (err: HttpErrorResponse) => {
          const message = err.status === 0
            ? 'Connection problem — check your internet and try again'
            : 'Could not start the scan. Please try again.';
          this.snackBar.open(message, 'Dismiss', { duration: 5000 });
        },
      });
  }
}
