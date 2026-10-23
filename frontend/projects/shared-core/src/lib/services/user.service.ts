import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { API_BASE_URL } from '../tokens';
import { ApiResponse } from '../models/api-response.model';
import {
  ClimateZone,
  ReasoningModelPreference,
  UserPreferences,
  VisionModelPreference,
} from '../models/user.model';

const SESSION_KEY = 'ai_model_preferences';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl: string;
  private readonly authUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(API_BASE_URL) apiBaseUrl: string,
  ) {
    this.baseUrl = `${apiBaseUrl}/users/me`;
    this.authUrl = `${apiBaseUrl}/auth`;
  }

  getPreferences(): Observable<ApiResponse<UserPreferences>> {
    return this.http.get<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`).pipe(
      tap(res => sessionStorage.setItem(SESSION_KEY, JSON.stringify(res.data))),
    );
  }

  /** Asks for a new verification email; the backend rate limits this per user (429). */
  resendVerificationEmail(): Observable<ApiResponse<null>> {
    return this.http.post<ApiResponse<null>>(`${this.authUrl}/verify-email/resend`, {});
  }

  updateModelPreferences(
    visionModelPreference: VisionModelPreference,
    reasoningModelPreference: ReasoningModelPreference,
  ): Observable<ApiResponse<UserPreferences>> {
    return this.http
      .put<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`, {
        visionModelPreference,
        reasoningModelPreference,
      })
      .pipe(tap(res => sessionStorage.setItem(SESSION_KEY, JSON.stringify(res.data))));
  }

  updatePlantNetPreferences(
    plantnetProject: string,
    plantnetLang: string,
  ): Observable<ApiResponse<UserPreferences>> {
    return this.http
      .put<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`, {
        plantnetProject,
        plantnetLang,
      })
      .pipe(tap(res => sessionStorage.setItem(SESSION_KEY, JSON.stringify(res.data))));
  }

  updateRegionPreference(
    regionCountry: string | undefined,
    regionClimate: ClimateZone | undefined,
  ): Observable<ApiResponse<UserPreferences>> {
    return this.http
      .put<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`, { regionCountry, regionClimate })
      .pipe(tap(res => sessionStorage.setItem(SESSION_KEY, JSON.stringify(res.data))));
  }

  updateBusinessTierPreference(businessTier: boolean): Observable<ApiResponse<UserPreferences>> {
    return this.http
      .put<ApiResponse<UserPreferences>>(`${this.baseUrl}/preferences`, { businessTier })
      .pipe(tap(res => sessionStorage.setItem(SESSION_KEY, JSON.stringify(res.data))));
  }
}
