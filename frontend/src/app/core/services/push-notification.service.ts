import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SwPush } from '@angular/service-worker';
import { Observable, from } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

interface SubscribeRequest {
  endpoint: string;
  keyP256dh: string;
  keyAuth: string;
}

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly baseUrl = `${environment.apiUrl}/notifications`;

  constructor(
    private readonly swPush: SwPush,
    private readonly http: HttpClient,
  ) {}

  async requestPermission(): Promise<boolean> {
    if (typeof Notification === 'undefined') return false;
    const permission = await Notification.requestPermission();
    return permission === 'granted';
  }

  subscribeToNotifications(): Observable<ApiResponse<void>> {
    return from(
      this.swPush.requestSubscription({ serverPublicKey: environment.vapidPublicKey }),
    ).pipe(
      switchMap(subscription => {
        const json = subscription.toJSON();
        const request: SubscribeRequest = {
          endpoint: json.endpoint ?? '',
          keyP256dh: json.keys?.['p256dh'] ?? '',
          keyAuth: json.keys?.['auth'] ?? '',
        };
        return this.http.post<ApiResponse<void>>(`${this.baseUrl}/subscribe`, request);
      }),
    );
  }
}
