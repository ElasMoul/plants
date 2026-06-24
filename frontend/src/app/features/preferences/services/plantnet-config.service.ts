import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api-response.model';

export interface PlantNetProject {
  id: string;
  name: string;
  commonNames?: Record<string, string>;
  languages?: string[];
}

export interface PlantNetQuota {
  remaining: number;
  total: number;
}

@Injectable()
export class PlantNetConfigService {
  private readonly baseUrl = `${environment.apiUrl}/plantnet`;

  constructor(private readonly http: HttpClient) {}

  getProjects(lang = 'en'): Observable<PlantNetProject[]> {
    return this.http
      .get<ApiResponse<PlantNetProject[]>>(`${this.baseUrl}/projects`, {
        params: { lang },
      })
      .pipe(map(res => res.data ?? []));
  }

  /** Returns plain language codes (e.g. "en", "fr") — PlantNet /v2/languages is a List<String>. */
  getLanguages(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.baseUrl}/languages`)
      .pipe(map(res => res.data ?? []));
  }

  /** Returns today's remaining Pl@ntNet identify quota (cached 5 min server-side). */
  getQuota(): Observable<PlantNetQuota | null> {
    return this.http
      .get<ApiResponse<PlantNetQuota>>(`${this.baseUrl}/quota`)
      .pipe(map(res => res.data ?? null));
  }
}
