import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '@plantpal/shared-core';

export type ReminderChannel = 'PUSH' | 'EMAIL' | 'BOTH';

export interface ReminderPreferences {
  channel: ReminderChannel;
  /** HH:mm, set together with quietHoursEnd or both null. */
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  timeZone: string;
  /** Read-only: true after the user used an email unsubscribe link. */
  emailUnsubscribed: boolean;
}

export type ReminderPreferencesUpdate = Omit<ReminderPreferences, 'emailUnsubscribed'>;

@Injectable({ providedIn: 'root' })
export class ReminderPreferencesService {
  private readonly baseUrl = `${environment.apiUrl}/notifications/preferences`;

  constructor(private readonly http: HttpClient) {}

  get(): Observable<ReminderPreferences> {
    return this.http.get<ApiResponse<ReminderPreferences>>(this.baseUrl).pipe(map(res => res.data));
  }

  save(update: ReminderPreferencesUpdate): Observable<ReminderPreferences> {
    return this.http
      .put<ApiResponse<ReminderPreferences>>(this.baseUrl, update)
      .pipe(map(res => res.data));
  }
}
