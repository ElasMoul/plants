import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, OnDestroy } from '@angular/core';
import { API_BASE_URL, AuthService } from '@plantpal/shared-core';
import { Subscription, timer } from 'rxjs';

export const HEARTBEAT_INTERVAL_MS = 60_000;

/**
 * Live-user presence (PLA-33): one body-less POST a minute while the tab is visible and the user
 * is signed in. Self-gating, so logging out simply stops the requests.
 */
@Injectable({ providedIn: 'root' })
export class PresenceHeartbeatService implements OnDestroy {
  private subscription?: Subscription;
  private readonly onVisibility = () => this.beat();

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) private readonly apiBaseUrl: string,
    private readonly authService: AuthService,
    @Inject(DOCUMENT) private readonly document: Document,
  ) {}

  start(): void {
    if (this.subscription) return;
    // timer(0, ...) sends one immediately; hidden tabs and signed-out users are skipped in beat().
    this.subscription = timer(0, HEARTBEAT_INTERVAL_MS).subscribe(() => this.beat());
    this.document.addEventListener('visibilitychange', this.onVisibility);
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    this.document.removeEventListener('visibilitychange', this.onVisibility);
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private beat(): void {
    if (this.document.visibilityState !== 'visible' || !this.authService.isLoggedIn()) return;
    this.http.post<void>(`${this.apiBaseUrl}/presence/heartbeat`, null).subscribe({ error: () => undefined });
  }
}
