import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { BusinessTierPromptComponent } from './business-tier-prompt.component';

const DISMISS_KEY = 'plantpal_business_tier_prompt_dismissed';

describe('BusinessTierPromptComponent', () => {
  let component: BusinessTierPromptComponent;
  let fixture: ComponentFixture<BusinessTierPromptComponent>;
  let router: { navigate: jest.Mock };

  beforeEach(() => {
    localStorage.clear();
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      declarations: [BusinessTierPromptComponent],
      providers: [{ provide: Router, useValue: router }],
      schemas: [NO_ERRORS_SCHEMA],
    });
    fixture = TestBed.createComponent(BusinessTierPromptComponent);
    component = fixture.componentInstance;
  });

  it('is hidden when below the plant threshold', () => {
    component.totalPlants = 9;
    component.businessTier = false;
    fixture.detectChanges();

    expect(component.visible).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-testid="business-tier-prompt"]')).toBeNull();
  });

  it('is visible at 10+ plants when not already business tier', () => {
    component.totalPlants = 10;
    component.businessTier = false;
    fixture.detectChanges();

    expect(component.visible).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="business-tier-prompt"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Your garden is growing');
  });

  it('is hidden when the user already self-declared businessTier', () => {
    component.totalPlants = 15;
    component.businessTier = true;
    fixture.detectChanges();

    expect(component.visible).toBe(false);
  });

  it('is hidden after being dismissed', () => {
    component.totalPlants = 10;
    component.businessTier = false;
    fixture.detectChanges();
    expect(component.visible).toBe(true);

    component.dismiss();
    fixture.detectChanges();

    expect(component.visible).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-testid="business-tier-prompt"]')).toBeNull();
  });

  it('persists dismissal in localStorage so it does not reappear on the next visit', () => {
    component.totalPlants = 10;
    component.businessTier = false;
    fixture.detectChanges();

    component.dismiss();

    expect(localStorage.getItem(DISMISS_KEY)).toBe('true');

    // Simulate a fresh page load re-reading the persisted dismissal.
    const freshFixture = TestBed.createComponent(BusinessTierPromptComponent);
    const freshComponent = freshFixture.componentInstance;
    freshComponent.totalPlants = 10;
    freshComponent.businessTier = false;
    freshFixture.detectChanges();

    expect(freshComponent.visible).toBe(false);
  });

  it('navigates to preferences when "Tell us more" is invoked', () => {
    component.totalPlants = 10;
    component.businessTier = false;
    fixture.detectChanges();

    component.goToPreferences();

    expect(router.navigate).toHaveBeenCalledWith(['/preferences']);
  });
});
