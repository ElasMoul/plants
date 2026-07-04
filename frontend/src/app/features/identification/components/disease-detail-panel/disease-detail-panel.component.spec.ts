import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { throwError } from 'rxjs';
import { AiErrorService } from '../../../../core/services/ai-error.service';
import { IdentificationService } from '../../services/identification.service';
import { DiseaseDetailPanelComponent } from './disease-detail-panel.component';

describe('DiseaseDetailPanelComponent', () => {
  let component: DiseaseDetailPanelComponent;
  let fixture: ComponentFixture<DiseaseDetailPanelComponent>;
  let getCureAdviceMock: jest.Mock;

  beforeEach(() => {
    getCureAdviceMock = jest.fn();

    TestBed.configureTestingModule({
      declarations: [DiseaseDetailPanelComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: IdentificationService, useValue: { getCureAdvice: getCureAdviceMock } },
        AiErrorService,
        { provide: MatSnackBar, useValue: { open: jest.fn() } },
      ],
    });

    fixture = TestBed.createComponent(DiseaseDetailPanelComponent);
    component = fixture.componentInstance;
    component.identificationId = 1;
    component.species = 'Monstera deliciosa';
    component.region = { label: 'leaf-spot', type: 'DISEASE', confidence: 'HIGH' } as never;
    fixture.detectChanges();
  });

  it('shows the threaded backend message and keeps Retry enabled for a non-blocking error', () => {
    getCureAdviceMock.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Service unavailable' } })),
    );

    component.askForCure();

    expect(component.adviceError).toBe(true);
    expect(component.adviceErrorMessage).toBe('Service unavailable');
    expect(component.adviceErrorBlocked).toBe(false);
  });

  it('shows the daily-limit message and suppresses Retry on a 402', () => {
    getCureAdviceMock.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 402, error: { message: 'app plantpal daily ceiling reached' } }),
      ),
    );

    component.askForCure();

    expect(component.adviceError).toBe(true);
    expect(component.adviceErrorMessage).toBe('Daily AI limit reached — try again tomorrow');
    expect(component.adviceErrorBlocked).toBe(true);
  });
});
