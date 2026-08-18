import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';
import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  let component: LandingComponent;
  let fixture: ComponentFixture<LandingComponent>;
  let authService: { isLoggedIn: jest.Mock };
  let router: { navigate: jest.Mock };

  beforeEach(() => {
    authService = { isLoggedIn: jest.fn().mockReturnValue(false) };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      declarations: [LandingComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    fixture = TestBed.createComponent(LandingComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('redirects to /home when the visitor is already logged in', () => {
    authService.isLoggedIn.mockReturnValue(true);

    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/home']);
  });

  it('does not redirect when the visitor is logged out', () => {
    authService.isLoggedIn.mockReturnValue(false);

    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('exposes the how-it-works steps and feature cards for the template', () => {
    fixture.detectChanges();

    expect(component.howItWorks.length).toBe(3);
    expect(component.features.length).toBe(5);
  });
});
