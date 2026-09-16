import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { API_BASE_URL, ApiResponse, AuthService } from '@plantpal/shared-core';
import { EMPTY, Subscription, timer } from 'rxjs';
import { catchError, exhaustMap } from 'rxjs/operators';
import { sanitizeReturnUrl } from '../return-url';

interface SessionStatus {
  active: boolean;
  secondsRemaining: number | null;
  revokedReason: string | null;
  enforcementActive: boolean;
}

const WARNING_THRESHOLD_SECONDS = 120;
const CHECK_INTERVAL_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class SessionMonitorService implements OnDestroy {
  private monitorSubscription?: Subscription;
  private warningVisible = false;

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) private readonly apiBaseUrl: string,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly snackBar: MatSnackBar,
  ) {}

  start(): void {
    if (this.monitorSubscription || !this.authService.isLoggedIn()) return;

    this.monitorSubscription = timer(0, CHECK_INTERVAL_MS)
      .pipe(
        exhaustMap(() => {
          // A concurrent protected request may already have received the decisive 401 and
          // cleared local auth. Do not let this still-live timer manufacture another redirect.
          if (!this.authService.isLoggedIn()) return EMPTY;
          return this.http.get<ApiResponse<SessionStatus>>(`${this.apiBaseUrl}/auth/session`).pipe(
            catchError(() => EMPTY),
          );
        }),
      )
      .subscribe(response => this.handleStatus(response.data));
  }

  stop(): void {
    this.monitorSubscription?.unsubscribe();
    this.monitorSubscription = undefined;
    this.warningVisible = false;
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private handleStatus(status: SessionStatus): void {
    // The registry is observed before it is enforced. While inactive, its answer must not
    // change a user's authenticated experience; Wave 5 alone authorizes that cutover.
    if (!status.enforcementActive) return;

    if (!status.active) {
      this.evict(status.revokedReason);
      return;
    }

    if (!this.warningVisible && (status.secondsRemaining ?? Infinity) <= WARNING_THRESHOLD_SECONDS) {
      this.warningVisible = true;
      this.snackBar
        .open('You will be signed out in two minutes due to inactivity.', 'Stay signed in')
        .onAction()
        .subscribe(() => this.renew());
    }
  }

  private renew(): void {
    this.http.post<ApiResponse<SessionStatus>>(`${this.apiBaseUrl}/auth/session/renew`, {}).subscribe({
      next: () => {
        this.warningVisible = false;
      },
      error: () => this.evict('IDLE_TIMEOUT'),
    });
  }

  private evict(reason: string | null): void {
    this.stop();
    this.authService.logout();
    const returnUrl = sanitizeReturnUrl(this.router.url);
    this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined);
    this.snackBar.open(reasonMessage(reason), 'Close', { duration: 6000 });
  }
}

export function reasonMessage(reason: string | null): string {
  switch (reason) {
    case 'IDLE_TIMEOUT':
      return 'You were signed out after 30 minutes of inactivity.';
    case 'ABSOLUTE_CAP':
      return 'Your 12-hour session ended. Please sign in again.';
    case 'PASSWORD_CHANGE':
      return 'Your password changed, so please sign in again.';
    case 'ADMIN':
      return 'Your session was ended. Please sign in again.';
    default:
      return 'Your session ended. Please sign in again.';
  }
}
