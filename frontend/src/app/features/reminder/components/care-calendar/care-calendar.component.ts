import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CareType, ReminderResponse } from '../../models/reminder.model';
import { careIcon as getCareIcon } from '../../models/care-icon.util';

interface CalendarDay {
  date: Date;
  label: string;
  dayNumber: string;
  isToday: boolean;
  reminders: ReminderResponse[];
  chips: DayChip[];
}

interface DayChip {
  careType: CareType;
  icon: string;
  count: number;
  tooltip: string;
}

export interface DaySelection {
  label: string;
  reminders: ReminderResponse[];
}

const DAYS_IN_WEEK = 7;

@Component({
    selector: 'app-care-calendar',
    templateUrl: './care-calendar.component.html',
    styleUrls: ['./care-calendar.component.scss'],
    standalone: false
})
export class CareCalendarComponent implements OnChanges {
  @Input() reminders: ReminderResponse[] = [];
  @Output() daySelected = new EventEmitter<DaySelection | null>();

  days: CalendarDay[] = [];
  selectedIndex: number | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reminders']) {
      this.buildDays();
      // Reminder list just reloaded (e.g. after marking one done) — drop any stale day filter
      // rather than risk showing a selection that no longer matches the rebuilt buckets.
      this.selectedIndex = null;
      this.daySelected.emit(null);
    }
  }

  selectDay(index: number, day: CalendarDay): void {
    if (this.selectedIndex === index) {
      this.selectedIndex = null;
      this.daySelected.emit(null);
      return;
    }
    this.selectedIndex = index;
    this.daySelected.emit({ label: day.isToday ? 'Today' : `${day.label} ${day.dayNumber}`, reminders: day.reminders });
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
        chips: [],
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

    for (const day of days) {
      day.chips = this.buildChips(day.reminders);
    }

    this.days = days;
  }

  // Collapses same-careType reminders on a day into one icon + a count badge, instead of one
  // icon per reminder (e.g. 3 plants due for watering today used to render 3 identical chips).
  private buildChips(reminders: ReminderResponse[]): DayChip[] {
    const byCareType = new Map<CareType, ReminderResponse[]>();
    for (const reminder of reminders) {
      const group = byCareType.get(reminder.careType);
      if (group) {
        group.push(reminder);
      } else {
        byCareType.set(reminder.careType, [reminder]);
      }
    }

    return Array.from(byCareType.entries()).map(([careType, group]) => ({
      careType,
      icon: getCareIcon(careType),
      count: group.length,
      tooltip: group.map(r => r.plantNickname).join(', '),
    }));
  }
}
