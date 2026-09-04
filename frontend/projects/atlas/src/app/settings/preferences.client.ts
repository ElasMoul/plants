import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, ApiResponse } from '@plantpal/shared-core';
import { map, Observable, tap } from 'rxjs';
import type { UserPreferencesDto } from '../world/world.dto';
import { SettingsStore } from './settings.store';

/** The classic app's own cache of the same answer, on the same origin. */
export const PREFS_CACHE_KEY = 'ai_model_preferences';

/**
 * The five server-backed preferences (the two model choices, the two PlantNet
 * fields, the business tier).
 *
 * Reads never trust the sessionStorage cache — the classic app or a previous mock
 * session may have written it — but writes push the server's answer back into it,
 * so a classic page on the same origin does not go stale behind this atlas.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesClient {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly settings = inject(SettingsStore);

  read(): Observable<UserPreferencesDto> {
    this.settings.prefsState.set('reading');
    return this.http.get<ApiResponse<UserPreferencesDto>>(`${this.base}/users/me/preferences`).pipe(
      tap({
        next: res => {
          this.settings.serverPrefs.set(res.data);
          this.settings.prefsState.set('idle');
        },
        error: (_err: HttpErrorResponse) => this.settings.prefsState.set('failed'),
      }),
      map(res => res.data),
    );
  }

  /** A partial PUT: only the keys given travel, so the other app's choices stand. */
  update(partial: Partial<UserPreferencesDto>): Observable<UserPreferencesDto> {
    return this.http
      .put<ApiResponse<UserPreferencesDto>>(`${this.base}/users/me/preferences`, partial)
      .pipe(
        tap(res => {
          this.settings.serverPrefs.set(res.data);
          this.settings.prefsState.set('idle');
          this.cache(res.data);
        }),
        map(res => res.data),
      );
  }

  private cache(prefs: UserPreferencesDto): void {
    try {
      sessionStorage.setItem(PREFS_CACHE_KEY, JSON.stringify(prefs));
    } catch {
      /* the cache is a courtesy to the classic app, never a requirement */
    }
  }
}
