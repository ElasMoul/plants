import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, ApiResponse } from '@plantpal/shared-core';
import { from, Observable, of, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';
import { MOCK_MODE } from '../core/mock-mode';
import { DeviceStore } from '../settings/device.store';
import { SettingsStore } from '../settings/settings.store';
import type { PushState, PushSubscriptionRequest } from '../world/world.dto';
import { WorldStore } from '../world/world.store';

/** VAPID keys travel as base64url; PushManager wants the raw bytes. */
export function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padded = (base64 + '='.repeat((4 - (base64.length % 4)) % 4))
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  const raw = atob(padded);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

/**
 * The device's push subscription, and the honest refusals around it. A knock is
 * not a reminder: everything here can be off while every reminder stays on, and
 * every way it can fail (no browser support, no server key, a denied permission)
 * is said in words rather than swallowed.
 *
 * A subscription is made at most once per device — POST /notifications/subscribe
 * inserts a row per call with no dedupe, so a remembered endpoint short-circuits.
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly device = inject(DeviceStore);
  private readonly settings = inject(SettingsStore);
  private readonly store = inject(WorldStore);
  private readonly mock = inject(MOCK_MODE, { optional: true });

  /** Where this device stands: on, off, or one of the three honest refusals. */
  state(): PushState {
    const remembered = !!this.device.state().push;
    if (this.mock?.enabled) return remembered ? 'on' : 'off';
    if (!this.supported()) return 'unsupported';
    if (!environment.vapidPublicKey) return 'unconfigured';
    if (Notification.permission === 'denied') return 'blocked';
    return remembered ? 'on' : 'off';
  }

  /** Ask this device to receive knocks. Refusals are spoken and leave the key off. */
  enable(): Observable<void> {
    if (this.device.state().push) {
      this.settings.set('notifications.push', 'on');
      this.store.say('Push is already on for this device.');
      return of(undefined);
    }
    if (this.mock?.enabled) {
      return this.register({ endpoint: 'mock://device', keyP256dh: 'mock', keyAuth: 'mock' });
    }
    if (!this.supported()) return this.refuse('This browser cannot receive pushes.');
    const key = environment.vapidPublicKey;
    if (!key) {
      return this.refuse(
        "Push needs this server's public key — it is not configured for this atlas.",
      );
    }
    let applicationServerKey: Uint8Array;
    try {
      applicationServerKey = urlBase64ToUint8Array(key);
    } catch {
      return this.refuse(
        "Push needs this server's public key — the one configured here could not be read.",
      );
    }
    return from(Notification.requestPermission()).pipe(
      switchMap(permission => {
        if (permission !== 'granted') return this.refuse('Push stays off — you did not allow it.');
        return from(navigator.serviceWorker.register('push-sw.js')).pipe(
          switchMap(reg =>
            from(
              reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey }),
            ),
          ),
          switchMap(sub => {
            const keys = (sub.toJSON().keys ?? {}) as Record<string, string>;
            return this.register({
              endpoint: sub.endpoint,
              keyP256dh: keys['p256dh'] ?? '',
              keyAuth: keys['auth'] ?? '',
            });
          }),
        );
      }),
    );
  }

  /** Stop knocking on this device. The server keeps its row until one fails. */
  disable(): Observable<void> {
    const done = () => {
      this.device.setPush(undefined);
      this.settings.set('notifications.push', 'off');
      this.store.say(
        'Push is off on this device. PlantPal keeps its record of it until a knock fails.',
      );
    };
    if (!this.mock?.enabled && this.supported()) {
      return from(navigator.serviceWorker.getRegistration()).pipe(
        switchMap(reg => from(reg?.pushManager.getSubscription() ?? Promise.resolve(null))),
        switchMap(sub => from(sub?.unsubscribe() ?? Promise.resolve(true))),
        switchMap(() => {
          done();
          return of(undefined as void);
        }),
      );
    }
    done();
    return of(undefined);
  }

  private supported(): boolean {
    return (
      typeof navigator !== 'undefined' &&
      'serviceWorker' in navigator &&
      typeof PushManager !== 'undefined' &&
      typeof Notification !== 'undefined'
    );
  }

  private refuse(message: string): Observable<void> {
    this.settings.set('notifications.push', 'off');
    this.store.say(message);
    return of(undefined);
  }

  private register(body: PushSubscriptionRequest): Observable<void> {
    return new Observable<void>(sub => {
      this.http.post<ApiResponse<void>>(`${this.base}/notifications/subscribe`, body).subscribe({
        next: () => {
          this.device.setPush({ endpoint: body.endpoint, subscribedAt: new Date().toISOString() });
          this.settings.set('notifications.push', 'on');
          this.store.say('Push is on for this device. Reminders themselves did not change.');
          sub.next();
          sub.complete();
        },
        error: (err: HttpErrorResponse) => {
          this.settings.set('notifications.push', 'off');
          this.store.say(
            (err.error as { message?: string } | undefined)?.message ??
              'PlantPal did not take the subscription. Push stays off on this device.',
          );
          sub.next();
          sub.complete();
        },
      });
    });
  }
}
