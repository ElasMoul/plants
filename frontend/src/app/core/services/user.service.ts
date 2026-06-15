import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AiModelPreference, UserPreferences } from '../models/user.model';

const SESSION_KEY = 'ai_model_preference';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl = `${environment.apiUrl}/users/me`;

  constructor(private readonly http: HttpClient) {}

  getPreferences(): Observable<ApiResponse<UserPreferences>> {
    const cached = sessionStorage.getItem(SESSION_KEY);
    if (cached) {
      return of({
        success: true,
        message: 'cached',
        data: { aiModelPreference: cached as AiModelPreference },
      });
    }
    return this.http.get<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`).pipe(
      tap(res => sessionStorage.setItem(SESSION_KEY, res.data.aiModelPreference)),
    );
  }

  updatePreferences(pref: AiModelPreference): Observable<ApiResponse<UserPreferences>> {
    return this.http
      .put<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`, { aiModelPreference: pref })
      .pipe(tap(() => sessionStorage.setItem(SESSION_KEY, pref)));
  }
}
