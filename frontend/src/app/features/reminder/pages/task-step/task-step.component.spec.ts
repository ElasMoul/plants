import { Location } from '@angular/common';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ReminderService } from '../../services/reminder.service';
import { TreatmentPlanService } from '../../services/treatment-plan.service';
import { TaskStepComponent } from './task-step.component';

describe('TaskStepComponent', () => {
  let plans: { getTreatmentPlan: jest.Mock };
  let reminders: { markCareDone: jest.Mock };
  let location: { back: jest.Mock };
  let router: { navigateByUrl: jest.Mock };
  let component: TaskStepComponent;

  const create = (query: Record<string, string> = {}) => {
    component = new TaskStepComponent(
      {
        snapshot: {
          paramMap: convertToParamMap({ planId: '3', stepId: '11' }),
          queryParamMap: convertToParamMap(query),
        },
      } as unknown as ActivatedRoute,
      location as unknown as Location,
      router as unknown as Router,
      plans as unknown as TreatmentPlanService,
      reminders as unknown as ReminderService,
    );
    component.ngOnInit();
  };

  beforeEach(() => {
    plans = {
      getTreatmentPlan: jest.fn(() =>
        of({ data: { steps: [{ id: 11, enabled: true, stepDetail: '1. Mix it. 2. Spray it.' }] } }),
      ),
    };
    reminders = { markCareDone: jest.fn(() => of({ data: {} })) };
    location = { back: jest.fn() };
    router = { navigateByUrl: jest.fn() };
  });

  it('loads its step from the plan and parses a numbered detail into a list', () => {
    create();
    expect(component.state).toBe('ready');
    expect(component.step?.id).toBe(11);
    expect(component.detailList?.items).toEqual(['Mix it.', 'Spray it.']);
  });

  it('shows an error for an unknown step or a failed load', () => {
    plans.getTreatmentPlan.mockReturnValueOnce(of({ data: { steps: [{ id: 99 }] } }));
    create();
    expect(component.state).toBe('error');

    plans.getTreatmentPlan.mockReturnValueOnce(throwError(() => new Error('500')));
    create();
    expect(component.state).toBe('error');
  });

  it('marking done logs the care and returns to the caller (returnUrl first)', () => {
    create({ returnUrl: '/plants/4?section=plan' });
    component.markDone();
    expect(reminders.markCareDone).toHaveBeenCalledWith(11);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/plants/4?section=plan');

    create();
    component.markDone();
    expect(location.back).toHaveBeenCalled();
  });

  it('an already-completed step (400) goes back; other errors stay put', () => {
    reminders.markCareDone.mockReturnValueOnce(throwError(() => ({ status: 400 })));
    create();
    component.markDone();
    expect(location.back).toHaveBeenCalledTimes(1);

    reminders.markCareDone.mockReturnValueOnce(throwError(() => ({ status: 500 })));
    component.markDone();
    expect(location.back).toHaveBeenCalledTimes(1);
    expect(component.markingDone).toBe(false);
  });

  it('never re-submits a disabled (done) step', () => {
    plans.getTreatmentPlan.mockReturnValueOnce(of({ data: { steps: [{ id: 11, enabled: false }] } }));
    create();
    component.markDone();
    expect(reminders.markCareDone).not.toHaveBeenCalled();
  });
});
