import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '@plantpal/shared-core';
import { SpeciesResponse, SpeciesSummaryDto } from '../models/species.model';

@Injectable()
export class SpeciesService {
  private readonly baseUrl = `${environment.apiUrl}/species`;

  constructor(private readonly http: HttpClient) {}

  getMySpecies(page = 0, size = 20): Observable<ApiResponse<PageResponse<SpeciesSummaryDto>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<SpeciesSummaryDto>>>(`${this.baseUrl}/mine`, { params });
  }

  getSpecies(id: number): Observable<ApiResponse<SpeciesResponse>> {
    return this.http.get<ApiResponse<SpeciesResponse>>(`${this.baseUrl}/${id}`);
  }

  regenerateDescription(id: number): Observable<ApiResponse<SpeciesResponse>> {
    return this.http.post<ApiResponse<SpeciesResponse>>(
      `${this.baseUrl}/${id}/regenerate-description`,
      {},
    );
  }
}
