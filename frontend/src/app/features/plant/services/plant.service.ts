import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '@plantpal/shared-core';
import { CreatePlantRequest, PlantResponse, SaveIdentificationAsPlantRequest, UpdatePlantRequest } from '../models/plant.model';

@Injectable()
export class PlantService {
  private readonly baseUrl = `${environment.apiUrl}/plants`;

  constructor(private readonly http: HttpClient) {}

  getPlants(
    page = 0,
    size = 20,
    speciesId?: number,
  ): Observable<ApiResponse<PageResponse<PlantResponse>>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'createdAt,desc');
    if (speciesId != null) {
      params = params.set('speciesId', speciesId);
    }
    return this.http.get<ApiResponse<PageResponse<PlantResponse>>>(this.baseUrl, { params });
  }

  getPlant(id: number): Observable<ApiResponse<PlantResponse>> {
    return this.http.get<ApiResponse<PlantResponse>>(`${this.baseUrl}/${id}`);
  }

  createPlant(request: CreatePlantRequest): Observable<ApiResponse<PlantResponse>> {
    return this.http.post<ApiResponse<PlantResponse>>(this.baseUrl, request);
  }

  updatePlant(id: number, request: UpdatePlantRequest): Observable<ApiResponse<PlantResponse>> {
    return this.http.put<ApiResponse<PlantResponse>>(`${this.baseUrl}/${id}`, request);
  }

  archivePlant(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`);
  }

  saveFromIdentification(request: SaveIdentificationAsPlantRequest): Observable<ApiResponse<PlantResponse>> {
    return this.http.post<ApiResponse<PlantResponse>>(`${this.baseUrl}/from-identification`, request);
  }
}
