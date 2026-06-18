import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CareCardDto, CareCardType } from '../../models/identification.model';
import { CareType } from '../../../reminder/models/reminder.model';
import { ReminderService } from '../../../reminder/services/reminder.service';
import { TreatmentPlanService } from '../../../reminder/services/treatment-plan.service';
import { SetReminderDialogComponent } from './set-reminder-dialog.component';

const CARD_COLORS: Record<CareCardType, string> = {
  WATERING:     '#1565C0',
  LIGHT:        '#F9A825',
  HUMIDITY:     '#0277BD',
  TEMPERATURE:  '#E65100',
  FERTILIZING:  '#558B2F',
  REPOTTING:    '#6D4C41',
  PRUNING:      '#00897B',
  PEST:         '#B71C1C',
  SEASONAL:     '#6A1B9A',
  BEGINNER_TIP: '#2E7D32',
};

@Component({
  selector: 'app-care-card',
  templateUrl: './care-card.component.html',
  styleUrls: ['./care-card.component.scss'],
})
export class CareCardComponent implements OnChanges, OnDestroy {
  @Input() card!: CareCardDto;
  @Input() plantId: number | null = null;
  @Input() existingCareTypes: CareType[] = [];

  expanded = false;
  reminderSet = false;
  reminderAlreadyExisted = false;
  settingReminder = false;
  startingTreatment = false;
  treatmentStarted = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly dialog: MatDialog,
    private readonly reminderService: ReminderService,
    private readonly treatmentPlanService: TreatmentPlanService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['card'] || changes['existingCareTypes']) {
      // AI only attaches a ROUTINE actionPlan to cards whose type maps to a real reminder CareType
      const alreadyExists = this.existingCareTypes.includes(this.card.type as CareType);
      this.reminderAlreadyExisted = alreadyExists;
      this.reminderSet = alreadyExists;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get accentColor(): string {
    return CARD_COLORS[this.card.type] ?? '#616161';
  }

  get accentBg(): string {
    return `${this.accentColor}1A`;
  }

  toggle(): void {
    this.expanded = !this.expanded;
  }

  openReminderDialog(event: Event): void {
    event.stopPropagation();
    const plantId = this.plantId;
    if (this.reminderSet || !this.card.actionPlan || plantId === null) return;

    const dialogRef = this.dialog.open(SetReminderDialogComponent, {
      width: '360px',
      maxWidth: '95vw',
      autoFocus: false,
      data: { cardTitle: this.card.title, frequencyDays: this.card.actionPlan.frequencyDays ?? 7 },
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((frequencyDays?: number) => {
        if (frequencyDays == null) return;
        this.settingReminder = true;
        this.reminderService.createReminder({
          plantId,
          careType: this.card.type as CareType,
          frequencyDays,
          firstDueAt: new Date().toISOString(),
        })
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.settingReminder = false;
              this.reminderSet = true;
            },
            error: () => {
              this.settingReminder = false;
              this.snackBar.open('Could not set reminder.', 'Dismiss', { duration: 4000 });
            },
          });
      });
  }

  startTreatmentPlan(event: Event): void {
    event.stopPropagation();
    const plantId = this.plantId;
    if (!this.card.actionPlan || this.startingTreatment || this.treatmentStarted || plantId === null) return;

    this.startingTreatment = true;
    this.treatmentPlanService.createFromActionPlan({
      plantId,
      title: this.card.title,
      sourceCareCardType: this.card.type,
      actionPlan: this.card.actionPlan,
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.startingTreatment = false;
          this.treatmentStarted = true;
          const snackRef = this.snackBar.open('Treatment plan started', 'View', { duration: 5000 });
          snackRef.onAction()
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
              this.router.navigate(['/treatment-plans', res.data.id]);
            });
        },
        error: () => {
          this.startingTreatment = false;
          this.snackBar.open('Could not start treatment plan.', 'Dismiss', { duration: 4000 });
        },
      });
  }
}
