import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '@plantpal/shared-core';
import { CareLogResponse } from '../models/care-log.model';

@Injectable()
export class CareLogService {
  private readonly baseUrl = `${environment.apiUrl}/care`;

  constructor(private readonly http: HttpClient) {}

  getPlantCareLogs(
    plantId: number,
    page = 0,
    size = 20,
  ): Observable<ApiResponse<PageResponse<CareLogResponse>>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get<ApiResponse<PageResponse<CareLogResponse>>>(
      `${this.baseUrl}/plant/${plantId}`,
      { params },
    );
  }
}
