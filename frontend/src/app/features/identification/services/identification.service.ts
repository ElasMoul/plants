import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api-response.model';
import { IdentificationResponse } from '../models/identification.model';

@Injectable()
export class IdentificationService {
  private readonly baseUrl = `${environment.apiUrl}/identifications`;

  constructor(private readonly http: HttpClient) {}

  analyze(
    images: File[],
    organs: string[],
    plantId?: number,
  ): Observable<ApiResponse<IdentificationResponse>> {
    const form = new FormData();
    // Append each image and organ as separate multipart parts (not arrays)
    images.forEach(img => form.append('images', img, img.name));
    organs.forEach(organ => form.append('organs', organ));
    if (plantId != null) {
      form.append('plantId', String(plantId));
    }
    return this.http.post<ApiResponse<IdentificationResponse>>(
      `${this.baseUrl}/analyze`,
      form,
    );
  }

  getCureAdvice(identificationId: number, regionLabel: string, species: string): Observable<string> {
    return this.http.post<ApiResponse<{ advice: string }>>(
      `${this.baseUrl}/${identificationId}/cure-advice`,
      { regionLabel, species },
    ).pipe(map(res => res.data.advice));
  }

  getPlantIdentifications(
    plantId: number,
    page = 0,
    size = 10,
  ): Observable<ApiResponse<PageResponse<IdentificationResponse>>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get<ApiResponse<PageResponse<IdentificationResponse>>>(
      `${this.baseUrl}/plant/${plantId}`,
      { params },
    );
  }
}
