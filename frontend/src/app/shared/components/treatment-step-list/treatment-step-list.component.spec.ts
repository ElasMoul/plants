import { ChangeDetectorRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ReminderResponse } from '../../../features/reminder/models/reminder.model';
import { ReminderService } from '../../../features/reminder/services/reminder.service';
import { TreatmentStepListComponent } from './treatment-step-list.component';

const NOW = new Date(2026, 8, 25, 23, 0); // 25 Sep 2026, 11 pm local

function step(id: number, due: Date, overrides: Partial<ReminderResponse> = {}): ReminderResponse {
  return {
    id,
    enabled: true,
    treatmentPlanId: 7,
    careType: 'PEST',
    nextDueAt: due.toISOString(),
    ...overrides,
  } as unknown as ReminderResponse;
}

describe('TreatmentStepListComponent', () => {
  let reminders: { markCareDone: jest.Mock };
  let router: { navigate: jest.Mock };
  let component: TreatmentStepListComponent;
  let completed: number;

  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(NOW);
    reminders = { markCareDone: jest.fn(() => of({ data: {} })) };
    router = { navigate: jest.fn() };
    component = new TreatmentStepListComponent(
      router as unknown as Router,
      reminders as unknown as ReminderService,
      { markForCheck: jest.fn() } as unknown as ChangeDetectorRef,
    );
    completed = 0;
    component.stepCompleted.subscribe(() => completed++);
  });

  afterEach(() => jest.useRealTimers());

  describe('dueLabel() counts calendar days, like the care calendar', () => {
    it('a step due at 8 am tomorrow is "Due in 1 day", not "Due today"', () => {
      expect(component.dueLabel(step(1, new Date(2026, 8, 26, 8, 0)))).toBe('Due in 1 day');
    });

    it('a step due late yesterday is overdue, not "Due today"', () => {
      jest.setSystemTime(new Date(2026, 8, 26, 8, 0));
      expect(component.dueLabel(step(1, new Date(2026, 8, 25, 23, 0)))).toBe('Overdue by 1 day');
    });

    it('anything due today reads "Due today"; plurals are right', () => {
      expect(component.dueLabel(step(1, new Date(2026, 8, 25, 1, 0)))).toBe('Due today');
      expect(component.dueLabel(step(1, new Date(2026, 8, 28, 12, 0)))).toBe('Due in 3 days');
      expect(component.dueLabel(step(1, new Date(2026, 8, 22, 12, 0)))).toBe('Overdue by 3 days');
    });
  });

  it('counts completed steps and falls back to the care type for a missing instruction', () => {
    component.steps = [step(1, NOW, { enabled: false }), step(2, NOW, { instruction: 'Spray' })];

    expect(component.completedCount).toBe(1);
    expect(component.totalCount).toBe(2);
    expect(component.stepInstruction(component.steps[0])).toBe('PEST');
    expect(component.stepInstruction(component.steps[1])).toBe('Spray');
    expect(component.hasStepDetail(step(3, NOW, { stepDetail: 'x' }))).toBe(true);
    expect(component.hasStepDetail(step(3, NOW))).toBe(false);
    expect(component.trackByStepId(0, component.steps[1])).toBe(2);
  });

  it('opens a step with the return URL', () => {
    component.returnUrl = '/treatments/5';
    component.openStepDetail(step(4, NOW));
    expect(router.navigate).toHaveBeenCalledWith(['/plans', 7, 'steps', 4], {
      queryParams: { returnUrl: '/treatments/5' },
    });
    component.openStepDetail(step(4, NOW, { treatmentPlanId: undefined }));
    expect(router.navigate).toHaveBeenCalledTimes(1);
  });

  it('marks a step done once and tells the parent; a 400 (already done) also re-syncs', () => {
    component.markStepDone(step(1, NOW));
    expect(reminders.markCareDone).toHaveBeenCalledWith(1);
    expect(completed).toBe(1);

    component.markStepDone(step(2, NOW, { enabled: false }));
    expect(reminders.markCareDone).toHaveBeenCalledTimes(1);

    reminders.markCareDone.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400 })));
    component.markStepDone(step(3, NOW));
    expect(completed).toBe(2);

    reminders.markCareDone.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.markStepDone(step(4, NOW));
    expect(completed).toBe(2);
    expect(component.markingDoneId).toBeNull();
  });
});
