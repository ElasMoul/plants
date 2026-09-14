import {
  HttpClient,
  HttpErrorResponse,
  HTTP_INTERCEPTORS,
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '@plantpal/shared-core';

import { JwtInterceptor } from './jwt.interceptor';

jest.mock('@sentry/angular', () => ({ setTag: jest.fn() }));

describe('JwtInterceptor sign-out behavior (wave 2 — PP-AUTH-002)', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let getToken: jest.MockedFunction<AuthService['getToken']>;
  let logout: jest.MockedFunction<AuthService['logout']>;
  let navigate: jest.Mock;

  beforeEach(() => {
    getToken = jest.fn().mockReturnValue('current-session-token');
    logout = jest.fn();
    navigate = jest.fn().mockResolvedValue(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true },
        { provide: AuthService, useValue: { getToken, logout } },
        { provide: Router, useValue: { navigate } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('latches concurrent 401 responses to exactly one logout and one /login navigation', () => {
    const urls = ['/api/v1/plants', '/api/v1/reminders', '/api/v1/dashboard'];
    const observedStatuses: number[] = [];

    for (const url of urls) {
      http.get(url).subscribe({
        error: (error: HttpErrorResponse) => observedStatuses.push(error.status),
      });
    }

    const concurrentRequests = httpTesting.match(request => urls.includes(request.url));
    expect(concurrentRequests).toHaveLength(urls.length);
    for (const request of concurrentRequests) {
      expect(request.request.headers.get('Authorization')).toBe('Bearer current-session-token');
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
    }

    // Every request still surfaces its own 401 to its caller — only the
    // sign-out side effect is deduplicated, not the error propagation.
    expect(observedStatuses).toEqual([401, 401, 401]);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not sign out on a 401 from the login endpoint itself (wrong password)', done => {
    http.post('/api/v1/auth/login', { email: 'a@b.com', password: 'wrong' }).subscribe({
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
        expect(logout).not.toHaveBeenCalled();
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });

    httpTesting
      .expectOne('/api/v1/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('does not sign out on a 401 from the register endpoint itself', done => {
    http.post('/api/v1/auth/register', {}).subscribe({
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(401);
        expect(logout).not.toHaveBeenCalled();
        expect(navigate).not.toHaveBeenCalled();
        done();
      },
    });

    httpTesting
      .expectOne('/api/v1/auth/register')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('latches again for a fresh 401 once the previous sign-out navigation has settled', async () => {
    http.get('/api/v1/plants').subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/v1/plants').flush(null, { status: 401, statusText: 'Unauthorized' });

    // Let the navigate().finally() microtask clear the latch.
    await Promise.resolve();
    await Promise.resolve();

    http.get('/api/v1/reminders').subscribe({ error: () => undefined });
    httpTesting
      .expectOne('/api/v1/reminders')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalledTimes(2);
    expect(navigate).toHaveBeenCalledTimes(2);
  });
});
