import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '@plantpal/shared-core';
import { delay, of, throwError } from 'rxjs';
import { MOCK_MODE } from '../core/mock-mode';
import { MockBackend } from './mock-backend';

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
  const { status, body } = backend.handle(req.method, req.urlWithParams.slice(base.length), req.body);
  const out$ =
    status >= 400
      ? throwError(() => new HttpErrorResponse({ status, statusText: 'Mock', url: req.url, error: body }))
      : of(new HttpResponse({ status, body: body ?? null, url: req.url }));
  return mode.latencyMs ? out$.pipe(delay(mode.latencyMs)) : out$;
};
