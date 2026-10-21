import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '@plantpal/shared-core';
import { GroupCareAction, GroupCareLog, PlantGroup } from '../models/plant-group.model';
import { PlantResponse } from '../models/plant.model';

@Injectable()
export class PlantGroupService {
  private readonly groupsUrl = `${environment.apiUrl}/groups`;
  private readonly plantsUrl = `${environment.apiUrl}/plants`;

  constructor(private readonly http: HttpClient) {}

  getGroups(): Observable<ApiResponse<PageResponse<PlantGroup>>> {
    const params = new HttpParams().set('page', 0).set('size', 100);
    return this.http.get<ApiResponse<PageResponse<PlantGroup>>>(this.groupsUrl, { params });
  }

  createGroup(name: string): Observable<ApiResponse<PlantGroup>> {
    return this.http.post<ApiResponse<PlantGroup>>(this.groupsUrl, { name });
  }

  renameGroup(id: number, name: string): Observable<ApiResponse<PlantGroup>> {
    return this.http.put<ApiResponse<PlantGroup>>(`${this.groupsUrl}/${id}`, { name });
  }

  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.groupsUrl}/${id}`);
  }

  logCare(id: number, action: GroupCareAction): Observable<ApiResponse<GroupCareLog[]>> {
    return this.http.post<ApiResponse<GroupCareLog[]>>(`${this.groupsUrl}/${id}/care`, { action });
  }

  /** A null groupId removes the plant from its group. */
  assignPlant(plantId: number, groupId: number | null): Observable<ApiResponse<PlantResponse>> {
    return this.http.put<ApiResponse<PlantResponse>>(`${this.plantsUrl}/${plantId}/group`, { groupId });
  }
}