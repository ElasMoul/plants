import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '@plantpal/shared-core';
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

  it('restarts the session monitor after a successful classic-app login', () => {
    const authService = { login: jest.fn(() => of(loginResponse)) } as unknown as AuthService;
    const router = { navigate: jest.fn(), navigateByUrl: jest.fn() } as unknown as Router;
    const route = { snapshot: { queryParamMap: { get: jest.fn(() => null) } } } as unknown as ActivatedRoute;
    const sessionMonitor = { start: jest.fn() } as unknown as SessionMonitorService;
    const component = new LoginComponent(
      new FormBuilder(),
      authService,
      router,
      route,
      { open: jest.fn() } as unknown as MatSnackBar,
      sessionMonitor,
    );
    component.form.setValue({ email: 'gardener@example.com', password: 'correct-password', openAtlas: false });

    component.submit();

    expect(router.navigate).toHaveBeenCalledWith(['/garden']);
    expect(sessionMonitor.start).toHaveBeenCalledTimes(1);
  });
});
