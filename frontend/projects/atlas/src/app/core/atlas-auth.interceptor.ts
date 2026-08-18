import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '@plantpal/shared-core';

/** A correlation id that also works over LAN HTTP (crypto.randomUUID needs a secure context). */
function correlationId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Attaches the shared session's bearer token (from the same AuthService + token
 * keys the classic app uses) and a correlation id to every request. This is the
 * atlas half of the shared JWT session: same-origin, a login in either app is a
 * login in both.
 */
export const atlasAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  const headers: Record<string, string> = { 'X-Correlation-ID': correlationId() };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return next(req.clone({ setHeaders: headers }));
};
