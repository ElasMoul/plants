import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ReminderService } from '../services/reminder.service';
import { CareType, ReminderResponse } from '../models/reminder.model';
import { DaySelection } from '../components/care-calendar/care-calendar.component';
import { careIcon as getCareIcon } from '../models/care-icon.util';
import { PLACEHOLDER_IMAGE } from '../../../shared/constants/placeholder-image.constant';
import { CreateReminderFormComponent } from '../components/create-reminder-form/create-reminder-form.component';

@Component({
  selector: 'app-reminder-list',
  templateUrl: './reminder-list.component.html',
  styleUrls: ['./reminder-list.component.scss'],
})
export class ReminderListComponent implements OnInit, OnDestroy {
  readonly placeholderImage = PLACEHOLDER_IMAGE;
  readonly skeletonRows = [1, 2, 3];

  reminders: ReminderResponse[] = [];
  loading = true;
  completingId: number | null = null;
  daySelection: DaySelection | null = null;

  // Checked synchronously at the top of completeReminder() — immune to the render-tick delay
  // the [disabled] template binding alone has, which a fast double-click/double-tap can beat.
  // Ids stay in this set permanently once a one-time reminder is done (button becomes "Done");
  // for recurring reminders the id is removed again once the new schedule loads.
  private readonly inFlightOrDoneIds = new Set<number>();

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly reminderService: ReminderService,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.loadReminders();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  careIcon(careType: CareType): string {
    return getCareIcon(careType);
  }

  isOverdue(reminder: ReminderResponse): boolean {
    return new Date(reminder.nextDueAt).getTime() < Date.now();
  }

  daysOverdue(reminder: ReminderResponse): number {
    const diff = Date.now() - new Date(reminder.nextDueAt).getTime();
    return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(CreateReminderFormComponent, {
      width: '420px',
      maxWidth: '95vw',
      autoFocus: false,
    });

    dialogRef.afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((created?: boolean) => {
        if (created) {
          this.loadReminders();
        }
      });
  }

  isDone(reminder: ReminderResponse): boolean {
    return !reminder.recurring && this.inFlightOrDoneIds.has(reminder.id);
  }

  completeReminder(reminder: ReminderResponse, event: Event): void {
    event.stopPropagation();
    if (this.inFlightOrDoneIds.has(reminder.id)) return;
    this.inFlightOrDoneIds.add(reminder.id);
    this.completingId = reminder.id;

    this.reminderService.markCareDone(reminder.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          if (reminder.recurring) {
            this.inFlightOrDoneIds.delete(reminder.id);
          }
          this.loadReminders();
        },
        error: (err: HttpErrorResponse) => {
          this.completingId = null;
          if (err.status === 400) {
            // Already completed (race with another tab/component) — treat as done, not an error.
            this.loadReminders();
            return;
          }
          this.inFlightOrDoneIds.delete(reminder.id);
          this.snackBar.open('Could not update reminder.', 'Dismiss', { duration: 4000 });
        },
      });
  }

  goToPlant(plantId: number): void {
    this.router.navigate(['/plants', plantId]);
  }

  get displayedReminders(): ReminderResponse[] {
    return this.daySelection?.reminders ?? this.reminders;
  }

  onDaySelected(selection: DaySelection | null): void {
    this.daySelection = selection;
  }

  private loadReminders(): void {
    this.loading = true;
    this.completingId = null;
    this.reminderService.getReminders()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.reminders = [...res.data].sort(
            (a, b) => new Date(a.nextDueAt).getTime() - new Date(b.nextDueAt).getTime(),
          );
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }
}
