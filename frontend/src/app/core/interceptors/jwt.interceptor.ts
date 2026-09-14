import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import * as Sentry from '@sentry/angular';
import { AuthService } from '@plantpal/shared-core';

// The public auth endpoints (ADR-5): a 401 from a wrong-password login attempt is
// the caller's own failed authentication, not evidence that an existing session
// died. Routing that through the same sign-out-and-redirect path as every other
// 401 is what let a login screen log itself out.
const AUTH_ENDPOINT_PATTERN = /\/auth\/(login|register)(?:[/?]|$)/;

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
          this.signOutOnce();
        }
        return throwError(() => error);
      }),
    );
  }

  private signOutOnce(): void {
    if (this.signOutInFlight) return;
    this.signOutInFlight = true;

    this.authService.logout();
    this.router.navigate(['/login']).finally(() => {
      this.signOutInFlight = false;
    });
  }
}
