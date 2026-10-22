import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '@plantpal/shared-core';
import { JournalEvent, JournalEventRequest } from '../models/journal-event.model';

@Injectable()
export class JournalEventService {
  constructor(private readonly http: HttpClient) {}

  list(plantId: number): Observable<ApiResponse<PageResponse<JournalEvent>>> {
    const params = new HttpParams().set('page', 0).set('size', 100);
    return this.http.get<ApiResponse<PageResponse<JournalEvent>>>(this.url(plantId), { params });
  }

  create(plantId: number, body: JournalEventRequest): Observable<ApiResponse<JournalEvent>> {
    return this.http.post<ApiResponse<JournalEvent>>(this.url(plantId), body);
  }

  update(plantId: number, id: number, body: JournalEventRequest): Observable<ApiResponse<JournalEvent>> {
    return this.http.put<ApiResponse<JournalEvent>>(`${this.url(plantId)}/${id}`, body);
  }

  delete(plantId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.url(plantId)}/${id}`);
  }

  private url(plantId: number): string {
    return `${environment.apiUrl}/plants/${plantId}/journal-events`;
  }
}
