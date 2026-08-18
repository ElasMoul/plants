import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import * as Sentry from '@sentry/angular';
import { AuthService } from '@plantpal/shared-core';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
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
        if (error.status === 401) {
          this.authService.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => error);
      }),
    );
  }
}
