import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { UserService } from '../../../core/services/user.service';
import { ModelSelectorComponent } from './model-selector.component';

const mockPreferences = {
  data: {
    visionModelPreference: 'GITHUB_GPT4O' as const,
    reasoningModelPreference: 'DEEPSEEK_R1' as const,
    visionModelAvailability: { ANTHROPIC_CLAUDE: false },
    reasoningModelAvailability: { ANTHROPIC_CLAUDE: false },
  },
};

describe('ModelSelectorComponent logic', () => {
  let component: ModelSelectorComponent;
  let fixture: ComponentFixture<ModelSelectorComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ModelSelectorComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {
          provide: UserService,
          useValue: {
            getPreferences: jest.fn().mockReturnValue(of(mockPreferences)),
            updateModelPreferences: jest.fn().mockReturnValue(of({})),
          },
        },
        {
          provide: MatSnackBar,
          useValue: { open: jest.fn().mockReturnValue({ onAction: () => ({ subscribe: jest.fn() }) }) },
        },
      ],
    });
    fixture = TestBed.createComponent(ModelSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('returns true for an available vision model', () => {
    expect(component.isVisionAvailable('GITHUB_GPT4O')).toBe(true);
  });

  it('returns false for a model explicitly disabled by the backend', () => {
    expect(component.isVisionAvailable('ANTHROPIC_CLAUDE')).toBe(false);
  });

  it('defaults to available when a model is absent from the availability map', () => {
    expect(component.isVisionAvailable('PLANTNET')).toBe(true);
  });

  it('selectedVisionOption returns the matching option for the current selection', () => {
    component.selectedVision = 'GITHUB_GPT4O';
    expect(component.selectedVisionOption?.value).toBe('GITHUB_GPT4O');
    expect(component.selectedVisionOption?.label).toBe('Best');
  });

  it('selectedVisionOption returns undefined for an unrecognised value', () => {
    (component as unknown as { selectedVision: string }).selectedVision = 'UNKNOWN_MODEL';
    expect(component.selectedVisionOption).toBeUndefined();
  });
});
