import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CareType, ReminderResponse } from '../../models/reminder.model';
import { careIcon as getCareIcon } from '../../models/care-icon.util';

interface CalendarDay {
  date: Date;
  label: string;
  dayNumber: string;
  isToday: boolean;
  reminders: ReminderResponse[];
}

const DAYS_IN_WEEK = 7;

@Component({
  selector: 'app-care-calendar',
  templateUrl: './care-calendar.component.html',
  styleUrls: ['./care-calendar.component.scss'],
})
export class CareCalendarComponent implements OnChanges {
  @Input() reminders: ReminderResponse[] = [];

  days: CalendarDay[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reminders']) {
      this.buildDays();
    }
  }

  careIcon(careType: CareType): string {
    return getCareIcon(careType);
  }

  private buildDays(): void {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const days: CalendarDay[] = [];
    for (let i = 0; i < DAYS_IN_WEEK; i++) {
      const date = new Date(today);
      date.setDate(today.getDate() + i);
      days.push({
        date,
        label: date.toLocaleDateString(undefined, { weekday: 'short' }),
        dayNumber: date.toLocaleDateString(undefined, { day: 'numeric' }),
        isToday: i === 0,
        reminders: [],
      });
    }

    for (const reminder of this.reminders) {
      const dueDate = new Date(reminder.nextDueAt);
      dueDate.setHours(0, 0, 0, 0);

      if (dueDate.getTime() <= today.getTime()) {
        days[0].reminders.push(reminder);
        continue;
      }
      const diffDays = Math.round((dueDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
      if (diffDays < DAYS_IN_WEEK) {
        days[diffDays].reminders.push(reminder);
      }
    }

    this.days = days;
  }
}
