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

describe('JwtInterceptor defect characterization', () => {
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

  it('[PP-AUTH-002][jwtinterceptor] fans out logout and /login navigation for concurrent 401 responses', () => {
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

    expect(observedStatuses).toEqual([401, 401, 401]);
    expect(logout).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenNthCalledWith(1, ['/login']);
    expect(navigate).toHaveBeenNthCalledWith(2, ['/login']);
    expect(navigate).toHaveBeenNthCalledWith(3, ['/login']);
  });
});
