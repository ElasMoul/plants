import { HttpErrorResponse } from '@angular/common/http';
import { discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AiErrorService } from '../../../../core/services/ai-error.service';
import { IdentificationService } from '../../../identification/services/identification.service';
import { TreatmentResponse } from '../../../plant/models/treatment.model';
import { PlantService } from '../../../plant/services/plant.service';
import { TreatmentService } from '../../../plant/services/treatment.service';
import { TreatmentPlanService } from '../../../reminder/services/treatment-plan.service';
import { TreatmentDetailComponent } from './treatment-detail.component';

function treatment(overrides: Partial<TreatmentResponse> = {}): TreatmentResponse {
  return {
    id: 5,
    plantId: 10,
    identificationId: 20,
    treatmentPlanId: 30,
    status: 'IN_PROGRESS',
    descriptionStatus: 'READY',
    ...overrides,
  } as TreatmentResponse;
}

describe('TreatmentDetailComponent', () => {
  let treatments: {
    getTreatment: jest.Mock;
    craftPlan: jest.Mock;
    completeTreatment: jest.Mock;
    regenerateDescription: jest.Mock;
  };
  let plans: { getTreatmentPlan: jest.Mock };
  let router: { navigate: jest.Mock };
  let aiError: { notify: jest.Mock };
  let component: TreatmentDetailComponent;

  const create = (section: string | null = null) => {
    component = new TreatmentDetailComponent(
      {
        snapshot: {
          paramMap: convertToParamMap({ id: '5' }),
          queryParamMap: convertToParamMap(section ? { section } : {}),
        },
      } as unknown as ActivatedRoute,
      router as unknown as Router,
      treatments as unknown as TreatmentService,
      { getPlant: jest.fn(() => of({ data: { id: 10, nickname: 'Monty' } })) } as unknown as PlantService,
      { getById: jest.fn(() => of({ data: { photoUrl: '/photos/scan.jpg' } })) } as unknown as IdentificationService,
      plans as unknown as TreatmentPlanService,
      aiError as unknown as AiErrorService,
    );
  };

  beforeEach(() => {
    treatments = {
      getTreatment: jest.fn(() => of({ data: treatment() })),
      craftPlan: jest.fn(() => of({ data: treatment({ treatmentPlanId: 31 }) })),
      completeTreatment: jest.fn(() => of({ data: treatment({ status: 'COMPLETED' }) })),
      regenerateDescription: jest.fn(() => of({ data: treatment({ descriptionStatus: 'PENDING' }) })),
    };
    plans = { getTreatmentPlan: jest.fn(() => of({ data: { id: 30, status: 'ACTIVE' } })) };
    router = { navigate: jest.fn() };
    aiError = { notify: jest.fn() };
    create();
  });

  afterEach(() => component.ngOnDestroy());

  it('loads the treatment, its plant, scan photo and plan', () => {
    component.ngOnInit();

    expect(component.state).toBe('ready');
    expect(component.plant?.nickname).toBe('Monty');
    expect(component.scanPhotoUrl).toBe('/photos/scan.jpg');
    expect(component.plan?.id).toBe(30);
    expect(component.planLoading).toBe(false);
  });

  it('opens the section requested in the URL and shows an error when loading fails', () => {
    create('plan');
    treatments.getTreatment.mockReturnValue(throwError(() => new Error('404')));
    component.ngOnInit();

    expect(component.activeSection).toBe('plan');
    expect(component.state).toBe('error');
  });

  describe('finishing the last plan step', () => {
    it('picks up the treatment the backend already completed instead of re-completing it', () => {
      component.ngOnInit();
      plans.getTreatmentPlan.mockReturnValue(of({ data: { id: 30, status: 'COMPLETED' } }));
      // The step-complete request already ran the backend's plan→treatment sync.
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ status: 'COMPLETED' }) }));
      treatments.completeTreatment.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 400 })),
      );

      component.onPlanStepCompleted();

      expect(treatments.completeTreatment).not.toHaveBeenCalled();
      expect(component.treatment?.status).toBe('COMPLETED');
    });

    it('still completes the treatment if the backend has not synced it', () => {
      component.ngOnInit();
      plans.getTreatmentPlan.mockReturnValue(of({ data: { id: 30, status: 'COMPLETED' } }));

      component.onPlanStepCompleted();

      expect(treatments.completeTreatment).toHaveBeenCalledWith(5);
      expect(component.treatment?.status).toBe('COMPLETED');
    });

    it('leaves an unfinished plan alone', () => {
      component.ngOnInit();

      component.onPlanStepCompleted();

      expect(treatments.completeTreatment).not.toHaveBeenCalled();
      expect(component.treatment?.status).toBe('IN_PROGRESS');
    });
  });

  describe('disease description polling', () => {
    it('keeps polling past 30s while the backend waits out a rate limit', fakeAsync(() => {
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ descriptionStatus: 'PENDING' }) }));
      component.ngOnInit();

      tick(60000);
      treatments.getTreatment.mockReturnValue(
        of({ data: treatment({ descriptionStatus: 'READY', diseaseDescription: 'Fungal.' }) }),
      );
      tick(3000);

      expect(component.treatment?.descriptionStatus).toBe('READY');
      expect(component.treatment?.diseaseDescription).toBe('Fungal.');
      discardPeriodicTasks();
    }));

    it('does not stop on a missing status — only READY or FAILED end the wait', fakeAsync(() => {
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ descriptionStatus: undefined }) }));
      component.ngOnInit();
      tick(3000);
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ descriptionStatus: 'FAILED' }) }));
      tick(3000);

      expect(component.treatment?.descriptionStatus).toBe('FAILED');
      discardPeriodicTasks();
    }));

    it('retrying a failed description regenerates and polls again', fakeAsync(() => {
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ descriptionStatus: 'FAILED' }) }));
      component.ngOnInit();
      treatments.getTreatment.mockReturnValue(of({ data: treatment({ descriptionStatus: 'READY' }) }));

      component.retryDescription();
      expect(component.treatment?.descriptionStatus).toBe('PENDING');
      tick(3000);

      expect(component.treatment?.descriptionStatus).toBe('READY');
      expect(component.retryingDescription).toBe(false);
      discardPeriodicTasks();
    }));
  });

  it('crafting a plan switches to the plan section; a failure is reported', () => {
    component.ngOnInit();
    component.craftPlan();
    expect(component.activeSection).toBe('plan');
    expect(plans.getTreatmentPlan).toHaveBeenLastCalledWith(31);

    treatments.craftPlan.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.craftPlan();
    expect(aiError.notify).toHaveBeenCalled();
    expect(component.craftingPlan).toBe(false);
  });

  it('navigates to the plant and lazily loads the plan section', () => {
    treatments.getTreatment.mockReturnValue(of({ data: treatment({ treatmentPlanId: undefined }) }));
    component.ngOnInit();
    component.goToPlant();
    expect(router.navigate).toHaveBeenCalledWith(['/plants', 10]);

    component.treatment = treatment();
    component.selectSection('plan');
    expect(plans.getTreatmentPlan).toHaveBeenCalledWith(30);
  });
});
