import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { DashboardService } from '../../services/dashboard.service';
import { CareType, DashboardResponse, ReminderSummaryDto } from '../../models/dashboard.model';
import { AuthService } from '../../../../core/services/auth.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { AnalyzeEmitPayload } from '../../../identification/models/identification.model';
import { IdentificationUploadDialogComponent } from '../../../identification/components/identification-upload-dialog/identification-upload-dialog.component';
import { PLACEHOLDER_IMAGE } from '../../../../shared/constants/placeholder-image.constant';

const CARE_ICONS: Record<CareType, string> = {
  WATERING: 'water_drop',
  LIGHT: 'wb_sunny',
  HUMIDITY: 'opacity',
  TEMPERATURE: 'thermostat',
  FERTILIZING: 'eco',
  REPOTTING: 'yard',
  PRUNING: 'content_cut',
  PEST: 'pest_control',
  SEASONAL: 'calendar_month',
  BEGINNER_TIP: 'lightbulb',
};

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly skeletonRows = [1, 2, 3];

  dashboard: DashboardResponse | null = null;
  loading = true;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly dashboardService: DashboardService,
    private readonly authService: AuthService,
    private readonly identificationService: IdentificationService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.dashboardService.getDashboard()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.dashboard = res.data;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get greeting(): string {
    const hour = new Date().getHours();
    const period = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
    const firstName = this.authService.getCurrentUser()?.firstName;
    return firstName ? `Good ${period}, ${firstName}` : `Good ${period}`;
  }

  get hasAnyData(): boolean {
    return !!this.dashboard && this.dashboard.healthSummary.totalPlants > 0;
  }

  get topTodayReminders(): ReminderSummaryDto[] {
    return this.dashboard?.todayReminders.slice(0, 3) ?? [];
  }

  careIcon(careType: CareType): string {
    return CARE_ICONS[careType];
  }

  reminderReadText(reminder: ReminderSummaryDto): string {
    return reminder.plantNickname + ': ' + reminder.careType.toLowerCase().replace(/_/g, ' ');
  }

  goToPlant(plantId: number): void {
    this.router.navigate(['/plants', plantId]);
  }

  goToScan(identificationId: number): void {
    this.router.navigate(['/identify', identificationId]);
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

  private submitIdentification(payload: AnalyzeEmitPayload): void {
    this.identificationService
      .analyze(payload.images, payload.organs, payload.plantId, payload.speciesId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open(
            'Identification started — view it in the Identify tab.',
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
