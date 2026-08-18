import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '@plantpal/shared-core';
import { CreateTreatmentPlanRequest, TreatmentPlanResponse } from '../models/treatment-plan.model';

@Injectable()
export class TreatmentPlanService {
  private readonly baseUrl = `${environment.apiUrl}/treatment-plans`;

  constructor(private readonly http: HttpClient) {}

  createFromActionPlan(
    request: CreateTreatmentPlanRequest,
  ): Observable<ApiResponse<TreatmentPlanResponse>> {
    return this.http.post<ApiResponse<TreatmentPlanResponse>>(this.baseUrl, request);
  }

  getTreatmentPlan(id: number): Observable<ApiResponse<TreatmentPlanResponse>> {
    return this.http.get<ApiResponse<TreatmentPlanResponse>>(`${this.baseUrl}/${id}`);
  }
}
