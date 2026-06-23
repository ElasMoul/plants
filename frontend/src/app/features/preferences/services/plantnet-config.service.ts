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

export interface PlantNetLanguage {
  code: string;
  label: string;
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

  getLanguages(): Observable<PlantNetLanguage[]> {
    return this.http
      .get<ApiResponse<PlantNetLanguage[]>>(`${this.baseUrl}/languages`)
      .pipe(map(res => res.data ?? []));
  }
}
