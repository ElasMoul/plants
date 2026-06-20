import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api-response.model';
import { SpeciesSummaryDto } from '../models/species.model';

@Injectable()
export class SpeciesService {
  private readonly baseUrl = `${environment.apiUrl}/species`;

  constructor(private readonly http: HttpClient) {}

  getMySpecies(page = 0, size = 20): Observable<ApiResponse<PageResponse<SpeciesSummaryDto>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<SpeciesSummaryDto>>>(`${this.baseUrl}/mine`, { params });
  }
}
