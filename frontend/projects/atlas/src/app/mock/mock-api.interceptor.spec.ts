import {
  HttpClient,
  HttpErrorResponse,
  HttpEventType,
  HttpResponse,
  provideHttpClient,
  withInterceptors,
  type HttpDownloadProgressEvent,
  type HttpEvent,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { MOCK_MODE, MockMode } from '../core/mock-mode';
import { mockApiInterceptor } from './mock-api.interceptor';

function configure(mode: MockMode) {
  TestBed.resetTestingModule();
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

describe('mockApiInterceptor — the chat stream', () => {
  function ask(latencyMs: number) {
    configure({ enabled: true, scenario: 'garden', latencyMs });
    const events: HttpEvent<string>[] = [];
    const sub = TestBed.inject(HttpClient)
      .post('/api/v1/chat/stream', { message: 'how often should I water?' }, {
        observe: 'events',
        responseType: 'text',
        reportProgress: true,
      })
      .subscribe(e => events.push(e));
    return { events, sub };
  }

  it('emits progress events with a growing partialText, then the response', () => {
    const { events } = ask(0);
    expect(events[0].type).toBe(HttpEventType.Sent);
    const progress = events.filter(
      (e): e is HttpDownloadProgressEvent => e.type === HttpEventType.DownloadProgress,
    );
    expect(progress.length).toBeGreaterThan(1);
    const lengths = progress.map(e => (e.partialText ?? '').length);
    expect([...lengths].sort((a, b) => a - b)).toEqual(lengths);
    const last = events[events.length - 1];
    expect(last.type).toBe(HttpEventType.Response);
    expect((last as HttpResponse<string>).body).toBe(progress[progress.length - 1].partialText);
    expect((last as HttpResponse<string>).body).toContain('data:');
    TestBed.inject(HttpTestingController).verify();
  });

  it('spaces the tokens over the configured latency and stops when unsubscribed', () => {
    jest.useFakeTimers();
    const { events, sub } = ask(1500);
    expect(events).toHaveLength(1);
    jest.advanceTimersByTime(1600);
    const before = events.length;
    expect(before).toBeGreaterThan(1);
    sub.unsubscribe();
    jest.advanceTimersByTime(5000);
    expect(events).toHaveLength(before);
    jest.useRealTimers();
  });

  it('leaves a non-streaming chat post on the single-response path', () => {
    configure({ enabled: true, scenario: 'garden', latencyMs: 0 });
    let body: { data: { reply: string } } | undefined;
    TestBed.inject(HttpClient)
      .post<{ data: { reply: string } }>('/api/v1/chat', { message: 'water?' })
      .subscribe(r => (body = r));
    expect(typeof body!.data.reply).toBe('string');
    TestBed.inject(HttpTestingController).verify();
  });

  it('passes a chat stream straight through in live mode', () => {
    configure({ enabled: false, scenario: 'garden', latencyMs: 0 });
    TestBed.inject(HttpClient)
      .post('/api/v1/chat/stream', {}, { observe: 'events', responseType: 'text', reportProgress: true })
      .subscribe();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/chat/stream').flush('data:hi\n\n');
    http.verify();
  });
});
