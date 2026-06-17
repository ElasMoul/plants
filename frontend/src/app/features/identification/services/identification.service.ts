import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, interval } from 'rxjs';
import { filter, map, startWith, switchMap, take, takeWhile, timeout } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../core/models/api-response.model';
import { IdentificationPendingResponse, IdentificationResponse } from '../models/identification.model';

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 32000;

@Injectable()
export class IdentificationService {
  private readonly baseUrl = `${environment.apiUrl}/identifications`;

  constructor(private readonly http: HttpClient) {}

  analyze(
    images: File[],
    organs: string[],
    plantId?: number,
  ): Observable<ApiResponse<IdentificationPendingResponse>> {
    const form = new FormData();
    // Append each image and organ as separate multipart parts (not arrays)
    images.forEach(img => form.append('images', img, img.name));
    organs.forEach(organ => form.append('organs', organ));
    if (plantId != null) {
      form.append('plantId', String(plantId));
    }
    return this.http.post<ApiResponse<IdentificationPendingResponse>>(
      `${this.baseUrl}/analyze`,
      form,
    );
  }

  getById(id: number): Observable<ApiResponse<IdentificationResponse>> {
    return this.http.get<ApiResponse<IdentificationResponse>>(`${this.baseUrl}/${id}`);
  }

  pollUntilComplete(id: number): Observable<IdentificationResponse> {
    return interval(POLL_INTERVAL_MS).pipe(
      startWith(0),
      switchMap(() => this.getById(id)),
      map(res => res.data),
      takeWhile(result => result.status === 'PENDING', true),
      filter(result => result.status !== 'PENDING'),
      map(result => {
        if (result.status === 'FAILED') {
          throw new Error('Identification failed');
        }
        return result;
      }),
      take(1),
      timeout(POLL_TIMEOUT_MS),
    );
  }

  getUserIdentifications(
    page = 0,
    size = 10,
  ): Observable<ApiResponse<PageResponse<IdentificationResponse>>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size);
    return this.http.get<ApiResponse<PageResponse<IdentificationResponse>>>(
      this.baseUrl,
      { params },
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
