import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '@plantpal/shared-core';
import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { SessionMonitorService } from '../../../core/services/session-monitor.service';
import { LoginComponent } from './login.component';

describe('LoginComponent session monitoring', () => {
  const loginResponse = {
    data: {
      token: 'fresh-session-token',
      user: { id: 1, email: 'gardener@example.com', firstName: 'Garden', lastName: 'User' },
    },
  };

  function buildComponent(returnUrlParam: string | null, role = "USER") {
    const authService = { login: jest.fn(() => of({...loginResponse,data:{...loginResponse.data,role}})) } as unknown as AuthService;
    const router = {
      navigate: jest.fn(() => Promise.resolve(true)),
      navigateByUrl: jest.fn(() => Promise.resolve(true)),
    } as unknown as Router;
    const route = {
      snapshot: { queryParamMap: { get: jest.fn(() => returnUrlParam) } },
    } as unknown as ActivatedRoute;
    const sessionMonitor = { start: jest.fn() } as unknown as SessionMonitorService;
    const component = new LoginComponent(
      new FormBuilder(),
      authService,
      router,
      route,
      { open: jest.fn() } as unknown as MatSnackBar,
      sessionMonitor,
    );
    component.form.setValue({ email: 'gardener@example.com', password: 'correct-password' });
    return { component, router, sessionMonitor };
  }

  it('restarts the session monitor after navigation to a successful classic-app login destination', fakeAsync(() => {
    const { component, router, sessionMonitor } = buildComponent(null);

    component.submit();

    tick();

    expect(router.navigate).toHaveBeenCalledWith(['/garden']);
    expect(sessionMonitor.start).toHaveBeenCalledWith('/garden');
  }));

  it('sends administrators to the console, even with a garden destination', fakeAsync(() => {
    const {component,router,sessionMonitor}=buildComponent('/garden/42', 'ADMIN');
    component.submit(); tick();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin');
    expect(sessionMonitor.start).toHaveBeenCalledWith('/admin');
  }));

  // Wave 2 acceptance: "a safe local destination is restored after login". The
  // signed-out deep link put the attempted URL in returnUrl (AuthGuard /
  // AuthCatchAllGuard); signing in must land the visitor back on it rather than
  // on the default landing route.
  it('restores a safe returnUrl destination after login', fakeAsync(() => {
    const { component, router, sessionMonitor } = buildComponent('/garden/42');
    const destination = '/garden/42';

    component.submit();

    tick();

    expect(router.navigateByUrl).toHaveBeenCalledWith(destination);
    expect(router.navigate).not.toHaveBeenCalled();
    // The monitor must be handed the restored destination too: if the fresh session is
    // evicted on its immediate poll, the pre-expiry fallback has to be the deep link,
    // not the transient /login route.
    expect(sessionMonitor.start).toHaveBeenCalledWith(destination);
  }));

  // The open-redirect boundary (return-url.ts) is asserted directly in
  // return-url.spec.ts. This is the component-level check that an unsafe value is
  // dropped rather than merely sanitized into a different navigable target.
  it('falls back to the default route when returnUrl is not a safe local destination', fakeAsync(() => {
    const { component, router, sessionMonitor } = buildComponent('https://evil.tld/steal');

    component.submit();

    tick();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/garden']);
    expect(sessionMonitor.start).toHaveBeenCalledWith('/garden');
  }));
});
