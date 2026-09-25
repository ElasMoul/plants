import { SimpleChange } from '@angular/core';
import { ReminderResponse } from '../../models/reminder.model';
import { CareCalendarComponent, DaySelection } from './care-calendar.component';

const NOW = new Date(2026, 8, 25, 14, 30); // 25 Sep 2026, mid-afternoon local time

function reminder(id: number, careType: string, dueAt: Date, plantNickname = `Plant ${id}`): ReminderResponse {
  return { id, careType, nextDueAt: dueAt.toISOString(), plantNickname } as unknown as ReminderResponse;
}

function daysFromNow(days: number, hour = 9): Date {
  return new Date(2026, 8, 25 + days, hour, 0);
}

describe('CareCalendarComponent', () => {
  let component: CareCalendarComponent;
  let emitted: (DaySelection | null)[];

  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(NOW);
    document.documentElement.lang = 'en';
    component = new CareCalendarComponent();
    emitted = [];
    component.daySelected.subscribe(selection => emitted.push(selection));
  });

  afterEach(() => jest.useRealTimers());

  const load = (reminders: ReminderResponse[]) => {
    component.reminders = reminders;
    component.ngOnChanges({ reminders: new SimpleChange(undefined, reminders, false) });
  };

  it('builds a 7-day strip starting today', () => {
    load([]);

    expect(component.days).toHaveLength(7);
    expect(component.days[0].isToday).toBe(true);
    expect(component.days[0].dayNumber).toBe('25');
    expect(component.days[6].dayNumber).toBe('1'); // rolls over into October
    expect(component.days.slice(1).every(d => !d.isToday)).toBe(true);
  });

  it('buckets reminders by day: overdue folds into today, beyond a week is dropped', () => {
    load([
      reminder(1, 'WATERING', daysFromNow(-3)),
      reminder(2, 'WATERING', daysFromNow(0, 23)),
      reminder(3, 'FERTILIZING', daysFromNow(1, 0)),
      reminder(4, 'PRUNING', daysFromNow(6, 23)),
      reminder(5, 'REPOTTING', daysFromNow(7)),
    ]);

    expect(component.days[0].reminders.map(r => r.id)).toEqual([1, 2]);
    expect(component.days[1].reminders.map(r => r.id)).toEqual([3]);
    expect(component.days[6].reminders.map(r => r.id)).toEqual([4]);
    expect(component.days.flatMap(d => d.reminders).map(r => r.id)).not.toContain(5);
  });

  it('collapses same-type reminders on a day into one chip with a count and names', () => {
    load([
      reminder(1, 'WATERING', daysFromNow(0), 'Monty'),
      reminder(2, 'WATERING', daysFromNow(0), 'Fig'),
      reminder(3, 'FERTILIZING', daysFromNow(0), 'Fig'),
    ]);

    const chips = component.days[0].chips;
    expect(chips).toHaveLength(2);
    expect(chips[0]).toMatchObject({ careType: 'WATERING', count: 2, tooltip: 'Monty, Fig' });
    expect(chips[0].icon).toBeTruthy();
    expect(chips[1]).toMatchObject({ careType: 'FERTILIZING', count: 1 });
  });

  it('selecting a day emits its reminders; selecting it again clears the filter', () => {
    load([reminder(1, 'WATERING', daysFromNow(1))]);
    emitted = [];

    component.selectDay(1, component.days[1]);
    expect(component.selectedIndex).toBe(1);
    expect(emitted[0]?.reminders.map(r => r.id)).toEqual([1]);
    expect(emitted[0]?.label).toContain('26');

    component.selectDay(0, component.days[0]);
    expect(emitted[1]?.label).toBe('Today');

    component.selectDay(0, component.days[0]);
    expect(component.selectedIndex).toBeNull();
    expect(emitted[2]).toBeNull();
  });

  it('a reloaded reminder list drops any stale day selection', () => {
    load([reminder(1, 'WATERING', daysFromNow(1))]);
    component.selectDay(1, component.days[1]);
    emitted = [];

    load([]);

    expect(component.selectedIndex).toBeNull();
    expect(emitted).toEqual([null]);
  });
});
