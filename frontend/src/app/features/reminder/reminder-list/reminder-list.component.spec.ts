import { ChangeDetectorRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ReminderResponse } from '../models/reminder.model';
import { ReminderService } from '../services/reminder.service';
import { ReminderListComponent } from './reminder-list.component';

function reminder(id: number, dueInHours: number, recurring = true): ReminderResponse {
  return {
    id,
    recurring,
    plantId: 100 + id,
    careType: 'WATERING',
    nextDueAt: new Date(Date.now() + dueInHours * 3_600_000).toISOString(),
  } as unknown as ReminderResponse;
}

describe('ReminderListComponent', () => {
  let service: { getReminders: jest.Mock; markCareDone: jest.Mock };
  let dialog: { open: jest.Mock };
  let snackBar: { open: jest.Mock };
  let router: { navigate: jest.Mock };
  let component: ReminderListComponent;
  const click = { stopPropagation: jest.fn() } as unknown as Event;

  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date(2026, 8, 25, 14, 30));
    service = {
      getReminders: jest.fn(() => of({ data: [reminder(1, 5), reminder(2, -30)] })),
      markCareDone: jest.fn(() => of({ data: {} })),
    };
    dialog = { open: jest.fn(() => ({ afterClosed: () => of(true) })) };
    snackBar = { open: jest.fn() };
    router = { navigate: jest.fn() };
    component = new ReminderListComponent(
      service as unknown as ReminderService,
      dialog as unknown as MatDialog,
      snackBar as unknown as MatSnackBar,
      router as unknown as Router,
      { markForCheck: jest.fn() } as unknown as ChangeDetectorRef,
    );
    component.ngOnInit();
  });

  afterEach(() => {
    component.ngOnDestroy();
    jest.useRealTimers();
  });

  it('loads reminders soonest-first and flags overdue ones', () => {
    expect(component.reminders.map(r => r.id)).toEqual([2, 1]);
    expect(component.loading).toBe(false);
    expect(component.isOverdue(component.reminders[0])).toBe(true);
    expect(component.daysOverdue(component.reminders[0])).toBe(1);
    expect(component.isOverdue(component.reminders[1])).toBe(false);
    expect(component.daysOverdue(component.reminders[1])).toBe(0);
  });

  it('a reminder due earlier today is due today, not "overdue by 0 days"', () => {
    const earlierToday = reminder(9, -2);
    expect(component.isOverdue(earlierToday)).toBe(false);
    expect(component.daysOverdue(earlierToday)).toBe(0);
  });

  it('a recurring reminder cannot be completed twice while its new schedule loads', () => {
    const reload = new Subject<{ data: ReminderResponse[] }>();
    service.getReminders.mockReturnValue(reload);
    const target = component.reminders[1];

    component.completeReminder(target, click);
    component.completeReminder(target, click); // second tap before the reload returns

    expect(service.markCareDone).toHaveBeenCalledTimes(1);

    reload.next({ data: [reminder(1, 24 * 7)] });
    component.completeReminder(component.reminders[0], click); // next occurrence: allowed again
    expect(service.markCareDone).toHaveBeenCalledTimes(2);
  });

  it('a one-time reminder shows as done and stays blocked', () => {
    const once = reminder(3, 2, false);
    component.completeReminder(once, click);
    component.completeReminder(once, click);

    expect(component.isDone(once)).toBe(true);
    expect(service.markCareDone).toHaveBeenCalledTimes(1);
  });

  it('an already-completed (400) reminder just reloads; other errors re-enable it and notify', () => {
    const target = component.reminders[0];
    service.markCareDone.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400 })));
    component.completeReminder(target, click);
    expect(snackBar.open).not.toHaveBeenCalled();
    expect(service.getReminders).toHaveBeenCalledTimes(2);

    const other = reminder(4, 1);
    service.markCareDone.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.completeReminder(other, click);
    expect(snackBar.open).toHaveBeenCalled();
    expect(component.completingId).toBeNull();
    component.completeReminder(other, click);
    expect(service.markCareDone).toHaveBeenCalledTimes(3);
  });

  it('filters by the selected calendar day and navigates to a plant', () => {
    component.onDaySelected({ label: 'Today', reminders: [component.reminders[0]] });
    expect(component.displayedReminders.map(r => r.id)).toEqual([2]);
    component.onDaySelected(null);
    expect(component.displayedReminders).toHaveLength(2);

    component.goToPlant(101);
    expect(router.navigate).toHaveBeenCalledWith(['/plants', 101]);
    expect(component.careIcon('WATERING' as never)).toBeTruthy();
    expect(component.trackByReminderId(0, component.reminders[0])).toBe(2);
  });

  it('reloads after a reminder is created from the dialog', () => {
    component.openCreateDialog();
    expect(dialog.open).toHaveBeenCalled();
    expect(service.getReminders).toHaveBeenCalledTimes(2);
  });

  it('stops the skeleton when loading fails', () => {
    service.getReminders.mockReturnValue(throwError(() => new Error('down')));
    component.openCreateDialog();
    expect(component.loading).toBe(false);
  });
});
