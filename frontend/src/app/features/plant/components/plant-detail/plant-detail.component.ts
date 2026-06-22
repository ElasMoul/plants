import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { HttpErrorResponse } from '@angular/common/http';
import { PlantService } from '../../services/plant.service';
import { TreatmentService } from '../../services/treatment.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { ReminderService } from '../../../reminder/services/reminder.service';
import { PlantResponse } from '../../models/plant.model';
import { TreatmentResponse } from '../../models/treatment.model';
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
import { PlantActionsSheetComponent, PlantAction } from '../plant-actions-sheet/plant-actions-sheet.component';
import { PlantScanHistorySheetComponent } from '../plant-scan-history-sheet/plant-scan-history-sheet.component';
import { ActiveTreatmentSelectSheetComponent } from '../active-treatment-select-sheet/active-treatment-select-sheet.component';


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
        // rootMargin pushes the effective viewport top down 40px, so the sentinel
        // (sitting at scrollY=0) only counts as "out of view" — i.e. collapse fires —
        // once the user has scrolled past 40px.
        { threshold: 0, rootMargin: '-40px 0px 0px 0px' },
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

  // Treatment CTA state for the currently-selected DISEASE region in the Scans section.
  activeTreatmentForDisease: TreatmentResponse | null = null;
  checkingActiveTreatment = false;
  startingTreatment = false;

  private sentinelObserver?: IntersectionObserver;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly plantService: PlantService,
    private readonly treatmentService: TreatmentService,
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

  selectSection(section: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans'): void {
    if (section === 'actions') {
      this.openActionsSheet();
      return;
    }
    if (section === 'treatment') {
      this.goToActiveTreatment();
      return;
    }
    this.activeSection = section;
  }

  openScanHistorySheet(): void {
    if (!this.plant) return;
    const ref = this.bottomSheet.open(PlantScanHistorySheetComponent, {
      data: { plantId: this.plant.id },
    });
    ref.afterDismissed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((scan?: IdentificationResponse) => {
        if (scan) {
          this.onScanSelected(scan);
        }
      });
  }

  startTreatment(): void {
    const plant = this.plant;
    const region = this.selectedRegion;
    const scan = this.selectedScan;
    if (!plant || !region || !scan || this.startingTreatment) return;

    // Stop at DRAFT and let the user trigger "Craft Treatment Plan" on the treatment page
    // themselves — matches the Overview flow, where the disease description (kicked off async by
    // createTreatment()) has time to finish before craft-plan's own AI call starts, instead of
    // racing it.
    this.startingTreatment = true;
    this.treatmentService.createTreatment(plant.id, scan.id, region.label)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: created => {
          this.startingTreatment = false;
          this.router.navigate(['/treatment', created.data.id]);
        },
        error: () => {
          this.startingTreatment = false;
          this.snackBar.open('Could not start a treatment plan.', 'Dismiss', { duration: 4000 });
        },
      });
  }

  goToActiveTreatment(): void {
    const plantId = this.plant?.id;
    if (!plantId) return;

    this.treatmentService.getActiveTreatments(plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const treatments = res.data;
          if (treatments.length === 1) {
            this.router.navigate(['/treatment', treatments[0].id]);
          } else if (treatments.length > 1) {
            this.openActiveTreatmentSelectSheet(treatments);
          }
        },
        error: () => {
          // Fall back to the last-known single active treatment if the list fetch fails.
          if (this.plant?.activeTreatmentId) {
            this.router.navigate(['/treatment', this.plant.activeTreatmentId]);
          }
        },
      });
  }

  private openActiveTreatmentSelectSheet(treatments: TreatmentResponse[]): void {
    const ref = this.bottomSheet.open(ActiveTreatmentSelectSheetComponent, {
      data: { treatments },
    });
    ref.afterDismissed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((treatment?: TreatmentResponse) => {
        if (treatment) {
          this.router.navigate(['/treatment', treatment.id]);
        }
      });
  }

  goToTreatment(treatment: TreatmentResponse): void {
    this.router.navigate(['/treatment', treatment.id]);
  }

  private openActionsSheet(): void {
    if (!this.plant) return;
    const ref = this.bottomSheet.open(PlantActionsSheetComponent, {
      data: { nickname: this.plant.nickname },
    });
    ref.afterDismissed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((action?: PlantAction) => this.handlePlantAction(action));
  }

  private handlePlantAction(action?: PlantAction): void {
    if (!action || !this.plant) return;
    switch (action) {
      case 'updatePhoto':
        this.snackBar.open('Updating the plant photo isn\'t available yet.', 'Dismiss', { duration: 4000 });
        break;
      case 'archive':
        if (window.confirm('Are you sure you want to archive this plant?')) {
          this.onArchive();
        }
        break;
      case 'scan':
        this.openAddScanDialog();
        break;
      case 'chat':
        this.router.navigate(['/chat'], { queryParams: { plantId: this.plant.id } });
        break;
    }
  }

  onScanSelected(scan: IdentificationResponse): void {
    this.selectedScan = scan;
    this.selectedRegionIndex = null;
    this.selectedRegion = null;
    this.activeTreatmentForDisease = null;
  }

  onRegionSelected(index: number | null): void {
    this.selectedRegionIndex = index;
    this.selectedRegion = index !== null && this.selectedScan?.annotationRegions
      ? (this.selectedScan.annotationRegions[index] ?? null)
      : null;

    this.activeTreatmentForDisease = null;
    if (this.selectedRegion?.type === 'DISEASE' && this.plant) {
      this.checkActiveTreatment(this.selectedRegion.label);
    }
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

  // The backend only tracks one active treatment per plant (most recent), not one per disease —
  // so "is there an active treatment for THIS disease" means: the plant's active treatment
  // exists AND its diseaseName matches the selected region's label.
  private checkActiveTreatment(diseaseName: string): void {
    const plantId = this.plant?.id;
    if (!plantId) return;

    this.checkingActiveTreatment = true;
    this.treatmentService.getActiveTreatment(plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.checkingActiveTreatment = false;
          this.activeTreatmentForDisease = res.data.diseaseName === diseaseName ? res.data : null;
        },
        error: () => {
          this.checkingActiveTreatment = false;
          this.activeTreatmentForDisease = null;
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
