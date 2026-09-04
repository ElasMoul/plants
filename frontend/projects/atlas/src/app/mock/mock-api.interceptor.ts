import {
  HttpErrorResponse,
  HttpEventType,
  HttpInterceptorFn,
  HttpResponse,
  type HttpEvent,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '@plantpal/shared-core';
import { defer, delay, Observable, of, throwError } from 'rxjs';
import { MOCK_MODE } from '../core/mock-mode';
import { frameToken } from '../world/sse-parse';
import { isChatStream, MockBackend, type MockReply } from './mock-backend';

/**
 * The mock garden's only seam. In live mode it is a pass-through, so
 * WorldGraphService, WorldActionsService and the assembly run through the very
 * same code path in both modes — mock and live can never drift.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  const mode = inject(MOCK_MODE, { optional: true });
  const base = inject(API_BASE_URL);
  if (!mode?.enabled || !req.url.startsWith(base)) return next(req);

  const backend = inject(MockBackend);
  // Deferred so a re-subscribe (retry/repeat) re-runs the request against the
  // in-memory backend, exactly as a real one would.
  const out$ = defer(() => {
    const reply = backend.handle(req.method, req.urlWithParams.slice(base.length), req.body);
    if (reply.status < 400 && req.reportProgress && req.url.endsWith('/chat/stream')) {
      return streamed(reply, req.url, mode.latencyMs);
    }
    // A stream read without progress reporting still gets what a real server
    // sends: the whole text/event-stream body, never the internal carrier.
    if (reply.status < 400 && isChatStream(reply.body)) {
      const text = reply.body.stream.map(frameToken).join('');
      return of(new HttpResponse({ status: reply.status, body: text, url: req.url }));
    }
    return reply.status >= 400
      ? throwError(() => new HttpErrorResponse({ status: reply.status, statusText: 'Mock', url: req.url, error: reply.body }))
      : of(new HttpResponse({ status: reply.status, body: reply.body ?? null, url: req.url }));
  });
  // A streamed reply spaces itself; delaying it again would delay only its Sent event.
  return mode.latencyMs && !(req.reportProgress && req.url.endsWith('/chat/stream'))
    ? out$.pipe(delay(mode.latencyMs))
    : out$;
};

/**
 * The chat stream, in the exact event shape a real chunked text/event-stream
 * produces: Sent, then DownloadProgress events carrying a GROWING cumulative
 * partialText, then the Response. Tokens are framed as Spring frames them, so
 * the atlas's own parser is exercised, not bypassed.
 *
 * Spacing is latencyMs / tokens; at zero latency every frame is emitted
 * synchronously, so a jsdom spec never waits. Unsubscribing stops the timer —
 * which is exactly how an abort is proved.
 */
function streamed(reply: MockReply, url: string, latencyMs: number): Observable<HttpEvent<string>> {
  const tokens = isChatStream(reply.body) ? reply.body.stream : [];
  return new Observable<HttpEvent<string>>(subscriber => {
    subscriber.next({ type: HttpEventType.Sent });
    let text = '';
    let index = 0;
    const emit = (): void => {
      text += frameToken(tokens[index++]);
      subscriber.next({
        type: HttpEventType.DownloadProgress,
        loaded: text.length,
        partialText: text,
      });
    };
    const finish = (): void => {
      subscriber.next(new HttpResponse({ status: 200, body: text, url }));
      subscriber.complete();
    };
    if (!latencyMs || tokens.length === 0) {
      while (index < tokens.length) emit();
      finish();
      return () => undefined;
    }
    const every = Math.max(1, Math.round(latencyMs / tokens.length));
    const timer = setInterval(() => {
      emit();
      if (index >= tokens.length) {
        clearInterval(timer);
        finish();
      }
    }, every);
    return () => clearInterval(timer);
  });
}
