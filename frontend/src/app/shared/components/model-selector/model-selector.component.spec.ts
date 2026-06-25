import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ModelSelectorComponent } from './model-selector.component';
import { UserService } from '../../../core/services/user.service';

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ModelSelectorComponent],
      providers: [
        {
          provide: UserService,
          useValue: {
            getPreferences: vi.fn().mockReturnValue(of(mockPreferences)),
            updateModelPreferences: vi.fn().mockReturnValue(of({})),
          },
        },
        {
          provide: MatSnackBar,
          useValue: { open: vi.fn().mockReturnValue({ onAction: () => ({ subscribe: vi.fn() }) }) },
        },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });

    // Don't call TestBed.createComponent — just test pure logic
    const userService = TestBed.inject(UserService) as any;
    const snackBar = TestBed.inject(MatSnackBar);
    component = new ModelSelectorComponent(userService, snackBar);
    // Manually apply the mock preferences
    (component as any).visionAvailability = { ANTHROPIC_CLAUDE: false };
    (component as any).reasoningAvailability = { ANTHROPIC_CLAUDE: false };
  });

  it('returns true for available vision model', () => {
    expect(component.isVisionAvailable('GITHUB_GPT4O')).toBe(true);
  });

  it('returns false for explicitly disabled vision model (ANTHROPIC_CLAUDE without key)', () => {
    expect(component.isVisionAvailable('ANTHROPIC_CLAUDE')).toBe(false);
  });

  it('defaults to available when model not in availability map', () => {
    expect(component.isVisionAvailable('PLANTNET')).toBe(true);
  });

  it('selectedVisionOption returns the matching option object', () => {
    component.selectedVision = 'GITHUB_GPT4O';
    expect(component.selectedVisionOption?.value).toBe('GITHUB_GPT4O');
    expect(component.selectedVisionOption?.label).toBe('Best');
  });

  it('selectedVisionOption returns undefined for unknown value', () => {
    (component as any).selectedVision = 'UNKNOWN_MODEL';
    expect(component.selectedVisionOption).toBeUndefined();
  });
});
