import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { MOCK_MODE, MockMode } from '../core/mock-mode';
import { mockApiInterceptor } from './mock-api.interceptor';

function configure(mode: MockMode) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([mockApiInterceptor])),
      provideHttpClientTesting(),
      ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
      { provide: MOCK_MODE, useValue: mode },
    ],
  });
}

describe('mockApiInterceptor (S1 — the only seam)', () => {
  it('answers a GET from the mock backend and reaches no HttpTestingController', () => {
    configure({ enabled: true, scenario: 'garden', latencyMs: 0 });
    let body: { data: { content: unknown[] } } | undefined;
    TestBed.inject(HttpClient).get<{ data: { content: unknown[] } }>('/api/v1/plants?size=50').subscribe(r => (body = r));
    expect(body!.data.content).toHaveLength(6);
    TestBed.inject(HttpTestingController).verify();
  });

  it('turns a 429 into an HttpErrorResponse carrying retryAfterSeconds', () => {
    configure({ enabled: true, scenario: 'garden', latencyMs: 0 });
    let err: HttpErrorResponse | undefined;
    TestBed.inject(HttpClient)
      .post('/api/v1/treatments/303/craft-plan', {})
      .subscribe({ error: (e: HttpErrorResponse) => (err = e) });
    expect(err!.status).toBe(429);
    expect(err!.error.retryAfterSeconds).toBe(900);
    TestBed.inject(HttpTestingController).verify();
  });

  it('passes the request through untouched when the mode is disabled', () => {
    configure({ enabled: false, scenario: 'garden', latencyMs: 0 });
    TestBed.inject(HttpClient).get('/api/v1/plants').subscribe();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/plants').flush({});
    http.verify();
  });

  it('delays every reply by the configured latency', () => {
    jest.useFakeTimers();
    configure({ enabled: true, scenario: 'garden', latencyMs: 1500 });
    let arrived = false;
    TestBed.inject(HttpClient).get('/api/v1/plants').subscribe(() => (arrived = true));
    jest.advanceTimersByTime(1000);
    expect(arrived).toBe(false);
    jest.advanceTimersByTime(600);
    expect(arrived).toBe(true);
    TestBed.inject(HttpTestingController).verify();
    jest.useRealTimers();
  });
});
