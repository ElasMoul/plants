import { translate } from '../../shared/i18n/language.service';
import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import * as Sentry from '@sentry/angular';
import { AuthService } from '@plantpal/shared-core';
import { reasonMessage } from '../services/session-monitor.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { sanitizeReturnUrl } from '../return-url';

// Auth-flow endpoints (ADR-5): a 401 from a wrong-password login attempt is
// the caller's own failed authentication, not evidence that an existing session
// died. Logout is also exempt because the caller always clears local state itself;
// routing that best-effort revocation through this side effect could create a second redirect.
const AUTH_ENDPOINT_PATTERN = /\/auth\/(login|register|logout)(?:[/?]|$)/;

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  // Deduplicated sign-out (ADR-5): N concurrent 401s must produce exactly one
  // logout+redirect, not N. The interceptor is a singleton HTTP_INTERCEPTORS
  // instance for the app's lifetime, so this latch is shared across every
  // in-flight request; it resets once the single redirect navigation settles,
  // so a genuinely new expiry later still triggers its own single flow.
  private signOutInFlight = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private snackBar: MatSnackBar,
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.authService.getToken();
    // crypto.randomUUID() requires a secure context (HTTPS/localhost); fall back
    // to a timestamp+random id so LAN HTTP access (192.168.x.x) still works.
    const correlationId =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;

    // Tag the active Sentry scope so frontend errors link to the backend trace
    Sentry.setTag('correlationId', correlationId);

    const headers: Record<string, string> = { 'X-Correlation-ID': correlationId };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    request = request.clone({ setHeaders: headers });

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && !AUTH_ENDPOINT_PATTERN.test(request.url)) {
          this.signOutOnce(error.headers.get('X-Session-Revoked-Reason'));
        }
        return throwError(() => error);
      }),
    );
  }

  private signOutOnce(reason: string | null): void {
    if (this.signOutInFlight) return;
    this.signOutInFlight = true;

    this.authService.logout();
    this.snackBar.open(reasonMessage(reason), translate('Close'), { duration: 6000 });
    const returnUrl = sanitizeReturnUrl(this.router.url);
    const extras = returnUrl ? { queryParams: { returnUrl } } : undefined;
    this.router.navigate(['/login'], extras).finally(() => {
      this.signOutInFlight = false;
    });
  }
}
