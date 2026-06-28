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
  CareCardDto,
  CarePlanDto,
  IdentificationResponse,
} from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';
import { CareType } from '../../../reminder/models/reminder.model';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';
import { PlantActionsSheetComponent, PlantAction } from '../plant-actions-sheet/plant-actions-sheet.component';
import { ActiveTreatmentSelectSheetComponent } from '../active-treatment-select-sheet/active-treatment-select-sheet.component';


@Component({
  selector: 'app-plant-detail',
  templateUrl: './plant-detail.component.html',
  styleUrls: ['./plant-detail.component.scss'],
})
export class PlantDetailComponent implements OnInit, OnDestroy {
  @ViewChild('scrollSentinel') set scrollSentinel(el: ElementRef<HTMLDivElement> | undefined) {
    if (el && !this.sentinelObserver) {
      this.sentinelObserver = new IntersectionObserver(
        ([entry]) => {
          this.headerCollapsed = !entry.isIntersecting;
        },
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
  existingCareTypes: CareType[] = [];

  headerCollapsed = false;
  activeSection: 'overview' | 'careLog' | 'actions' | 'treatment' | 'scans' = 'overview';

  // Scan history list state (T10.D)
  scanHistory: IdentificationResponse[] = [];
  scanHistoryLoaded = false;
  loadingScanHistory = false;

  // All loaded identifications — feeds both the merged overview care plan and the scans list.
  allIdentifications: IdentificationResponse[] = [];

  private plantId!: number;
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
    const section = this.route.snapshot.queryParamMap.get('section') as typeof this.activeSection | null;
    if (section && ['overview', 'careLog', 'scans'].includes(section)) {
      this.activeSection = section;
    }

    this.plantId = Number(this.route.snapshot.paramMap.get('id'));

    this.plantService.getPlant(this.plantId).subscribe({
      next: (res) => {
        this.plant = res.data;
        this.loading = false;
      },
      error: () => {
        this.snackBar.open('Plant not found.', 'Dismiss', { duration: 4000 });
        this.router.navigate(['/garden']);
      },
    });

    this.identificationService.getPlantIdentifications(this.plantId, 0, 10)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const items = res.data.content;
          this.hasIdentification = items.length > 0;
          if (items.length > 0) {
            this.latestCarePlan = items[0].carePlan;
            this.latestIdentificationId = items[0].id;
            this.allIdentifications = items;
          }
          this.carePlanLoaded = true;
          if (this.activeSection === 'scans') {
            this.loadScanHistory();
          }
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
            .filter(r => r.plantId === this.plantId && r.enabled)
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
    if (section === 'scans' && !this.scanHistoryLoaded) {
      this.loadScanHistory();
    }
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

  openScanDetail(scan: IdentificationResponse): void {
    this.router.navigate(['/plants', this.plantId, 'scans', scan.id]);
  }

  scanHealthLabel(scan: IdentificationResponse): string {
    switch (scan.healthStatus) {
      case 'HEALTHY': return 'Healthy';
      case 'ISSUES_DETECTED': return 'Issues detected';
      default: return 'Unknown';
    }
  }

  scanHealthClass(scan: IdentificationResponse): string {
    switch (scan.healthStatus) {
      case 'HEALTHY': return 'health-healthy';
      case 'ISSUES_DETECTED': return 'health-issues';
      default: return 'health-unknown';
    }
  }

  loadScanHistory(): void {
    if (this.loadingScanHistory) return;
    this.loadingScanHistory = true;
    this.identificationService.getPlantIdentifications(this.plantId, 0, 20)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.scanHistory = res.data.content;
          this.allIdentifications = res.data.content;
          this.scanHistoryLoaded = true;
          this.loadingScanHistory = false;
        },
        error: () => {
          this.scanHistoryLoaded = true;
          this.loadingScanHistory = false;
        },
      });
  }

  get mergedCarePlan(): CarePlanDto | null {
    if (!this.allIdentifications.length) return this.latestCarePlan;
    const seen = new Set<string>();
    const cards: CareCardDto[] = [];
    for (const scan of this.allIdentifications) {
      if (scan.status === 'COMPLETED' && scan.carePlan?.careCards) {
        for (const card of scan.carePlan.careCards) {
          if (!seen.has(card.type)) {
            seen.add(card.type);
            cards.push(card);
          }
        }
      }
    }
    if (!cards.length) return this.latestCarePlan;
    const base: CarePlanDto = this.latestCarePlan ?? {
      wateringFrequencyDays: 0,
      fertilizingFrequencyDays: 0,
      repottingFrequencyMonths: 0,
      careCards: [],
      beginnerWarnings: [],
    };
    return { ...base, careCards: cards };
  }

  private submitAddScan(payload: AnalyzeEmitPayload): void {
    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId ?? this.plant?.id, undefined, payload.userContext)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open('Scan started — it will appear in the list shortly.', undefined, {
            duration: 4000,
          });
          this.scanHistoryLoaded = false;
          this.scanHistory = [];
          // Reload immediately so the new PENDING scan is visible without leaving the section.
          if (this.activeSection === 'scans') {
            this.loadScanHistory();
          }
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
