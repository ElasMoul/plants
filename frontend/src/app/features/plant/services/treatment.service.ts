import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api-response.model';
import { TreatmentResponse } from '../models/treatment.model';

@Injectable()
export class TreatmentService {
  private readonly baseUrl = `${environment.apiUrl}`;

  constructor(private readonly http: HttpClient) {}

  createTreatment(
    plantId: number,
    identificationId: number,
    diseaseName: string,
  ): Observable<ApiResponse<TreatmentResponse>> {
    return this.http.post<ApiResponse<TreatmentResponse>>(`${this.baseUrl}/treatments`, {
      plantId,
      identificationId,
      diseaseName,
    });
  }

  craftPlan(treatmentId: number): Observable<ApiResponse<TreatmentResponse>> {
    return this.http.post<ApiResponse<TreatmentResponse>>(
      `${this.baseUrl}/treatments/${treatmentId}/craft-plan`,
      {},
    );
  }

  getTreatment(id: number): Observable<ApiResponse<TreatmentResponse>> {
    return this.http.get<ApiResponse<TreatmentResponse>>(`${this.baseUrl}/treatments/${id}`);
  }

  getActiveTreatments(plantId: number): Observable<ApiResponse<TreatmentResponse[]>> {
    return this.http.get<ApiResponse<TreatmentResponse[]>>(
      `${this.baseUrl}/plants/${plantId}/active-treatments`,
    );
  }

  completeTreatment(id: number): Observable<ApiResponse<TreatmentResponse>> {
    return this.http.patch<ApiResponse<TreatmentResponse>>(
      `${this.baseUrl}/treatments/${id}/complete`,
      {},
    );
  }
}
