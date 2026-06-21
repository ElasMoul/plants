import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CareCardDto, CareCardType } from '../../models/identification.model';
import { CareType } from '../../../reminder/models/reminder.model';
import { ReminderService } from '../../../reminder/services/reminder.service';
import { TreatmentService } from '../../../plant/services/treatment.service';
import { SetReminderDialogComponent } from './set-reminder-dialog.component';
import { parseDetailAsList, ParsedDetail } from '../../../../shared/utils/detail-list.util';

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
  @Input() identificationId: number | null = null;
  @Input() existingCareTypes: CareType[] = [];

  expanded = false;
  detailList: ParsedDetail | null = null;
  reminderSet = false;
  reminderAlreadyExisted = false;
  settingReminder = false;
  startingTreatment = false;
  treatmentStarted = false;
  activeTreatmentId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly dialog: MatDialog,
    private readonly reminderService: ReminderService,
    private readonly treatmentService: TreatmentService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['card']) {
      this.detailList = parseDetailAsList(this.card.detail);
    }
    if (changes['card'] || changes['existingCareTypes']) {
      // AI only attaches a ROUTINE actionPlan to cards whose type maps to a real reminder CareType
      const alreadyExists = this.existingCareTypes.includes(this.card.type as CareType);
      this.reminderAlreadyExisted = alreadyExists;
      this.reminderSet = alreadyExists;
    }
    if ((changes['card'] || changes['plantId']) && this.card?.actionPlan?.type === 'TREATMENT') {
      this.checkActiveTreatment();
    }
  }

  // The backend tracks only one active treatment per plant (most recent), not one per disease —
  // matches the same check plant-detail.component.ts uses for the Scans-tab CTA. Deriving
  // treatmentStarted from the real Treatment entity (instead of a session-only flag) means the
  // button correctly shows "Plan in progress" even after a page refresh, and — together with
  // TreatmentService.createTreatment()'s own duplicate check — is what stops this card and any
  // other entry point (e.g. the Scans-tab CTA) from starting two treatments for the same disease.
  private checkActiveTreatment(): void {
    const plantId = this.plantId;
    if (plantId === null) return;

    this.treatmentService.getActiveTreatment(plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          const matches = res.data.diseaseName === this.card.title;
          this.treatmentStarted = matches;
          this.activeTreatmentId = matches ? res.data.id : null;
        },
        error: () => {
          this.treatmentStarted = false;
          this.activeTreatmentId = null;
        },
      });
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
    const identificationId = this.identificationId;
    if (!this.card.actionPlan || this.startingTreatment || this.treatmentStarted) return;
    if (plantId === null || identificationId === null) return;

    this.startingTreatment = true;
    // Goes through the Treatment entity (createTreatment + craftPlan), not
    // TreatmentPlanService.createFromActionPlan() directly — createTreatment() rejects a second
    // active treatment for the same plant+diseaseName, which is what actually stops duplicates;
    // see ARCHITECT.md's "Two Treatment concepts".
    this.treatmentService.createTreatment(plantId, identificationId, this.card.title)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: created => {
          this.treatmentService.craftPlan(created.data.id)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
              next: crafted => {
                this.startingTreatment = false;
                this.treatmentStarted = true;
                this.activeTreatmentId = crafted.data.id;
                const snackRef = this.snackBar.open('Treatment plan started', 'View', { duration: 5000 });
                snackRef.onAction()
                  .pipe(takeUntil(this.destroy$))
                  .subscribe(() => this.router.navigate(['/treatment', crafted.data.id]));
              },
              error: () => {
                this.startingTreatment = false;
                this.treatmentStarted = true;
                this.activeTreatmentId = created.data.id;
                this.snackBar.open('Treatment started, but the plan could not be crafted yet.', 'Dismiss', { duration: 5000 });
              },
            });
        },
        error: () => {
          this.startingTreatment = false;
          this.snackBar.open('Could not start treatment plan.', 'Dismiss', { duration: 4000 });
        },
      });
  }

  goToTreatment(event: Event): void {
    event.stopPropagation();
    if (this.activeTreatmentId !== null) {
      this.router.navigate(['/treatment', this.activeTreatmentId]);
    }
  }
}
