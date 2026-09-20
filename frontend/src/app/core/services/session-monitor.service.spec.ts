import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { API_BASE_URL, AuthService } from '@plantpal/shared-core';
import { EMPTY } from 'rxjs';

import { reasonMessage, SessionMonitorService } from './session-monitor.service';

describe('SessionMonitorService', () => {
  let service: SessionMonitorService;
  let httpTesting: HttpTestingController;
  let logout: jest.Mock;
  let navigate: jest.Mock;
  let snackBar: { open: jest.Mock };
  let loggedIn: boolean;

  beforeEach(() => {
    logout = jest.fn();
    navigate = jest.fn();
    snackBar = { open: jest.fn(() => ({ onAction: () => EMPTY })) };
    loggedIn = true;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' },
        { provide: AuthService, useValue: { isLoggedIn: () => loggedIn, logout } },
        { provide: Router, useValue: { navigate, url: '/treatment/42' } },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    service = TestBed.inject(SessionMonitorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stop();
    httpTesting.verify();
  });

  it('observes inactive registry status without evicting while enforcement is inert', fakeAsync(() => {
    service.start();
    tick(1);
    httpTesting.expectOne('/api/v1/auth/session').flush({
      data: { active: false, secondsRemaining: null, revokedReason: 'IDLE_TIMEOUT', enforcementActive: false },
    });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(snackBar.open).not.toHaveBeenCalled();
    // fakeAsync flushes a live periodic timer before afterEach runs. Stop the
    // monitor while still inside the fakeAsync lifecycle so it cannot begin a
    // second, deliberately unflushed status poll during that cleanup phase.
    service.stop();
  }));

  it('warns two minutes before an enforced inactivity expiry without renewing by polling', fakeAsync(() => {
    service.start();
    tick(1);
    httpTesting.expectOne('/api/v1/auth/session').flush({
      data: { active: true, secondsRemaining: 120, revokedReason: null, enforcementActive: true },
    });

    expect(snackBar.open).toHaveBeenCalledWith(
      'You will be signed out in two minutes due to inactivity.',
      'Stay signed in',
    );
    httpTesting.expectNone('/api/v1/auth/session/renew');
    // See the lifecycle note in the inert-registry test above.
    service.stop();
  }));

  it('uses a reason-specific message when the enforced registry expires', fakeAsync(() => {
    service.start();
    tick(1);
    httpTesting.expectOne('/api/v1/auth/session').flush({
      data: { active: false, secondsRemaining: null, revokedReason: 'ABSOLUTE_CAP', enforcementActive: true },
    });

    expect(logout).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/treatment/42' } });
    expect(snackBar.open).toHaveBeenCalledWith(
      'Your 12-hour session ended. Please sign in again.',
      'Close',
      { duration: 6000 },
    );
  }));

  it('keeps the supplied safe destination when an immediate poll still sees the login route', fakeAsync(() => {
    (TestBed.inject(Router) as unknown as { url: string }).url = '/login?returnUrl=%2Fgarden';
    service.start('/garden');
    tick(1);
    httpTesting.expectOne('/api/v1/auth/session').flush({
      data: { active: false, secondsRemaining: null, revokedReason: 'IDLE_TIMEOUT', enforcementActive: true },
    });

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/garden' } });
  }));

  it('does not start another poll after another request has already signed the user out', fakeAsync(() => {
    service.start();
    tick(1);
    httpTesting.expectOne('/api/v1/auth/session').flush({
      data: { active: true, secondsRemaining: 1800, revokedReason: null, enforcementActive: true },
    });

    loggedIn = false;
    tick(30_000);

    httpTesting.expectNone('/api/v1/auth/session');
    service.stop();
  }));
});

describe('reasonMessage', () => {
  it.each([
    ['IDLE_TIMEOUT', 'You were signed out after 30 minutes of inactivity.'],
    ['PASSWORD_CHANGE', 'Your password changed, so please sign in again.'],
    ['ADMIN', 'Your session was ended. Please sign in again.'],
  ])('describes %s', (reason, expected) => {
    expect(reasonMessage(reason)).toBe(expected);
  });
});
