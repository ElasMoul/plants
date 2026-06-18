import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api-response.model';
import { CreateReminderRequest, ReminderResponse } from '../models/reminder.model';

@Injectable()
export class ReminderService {
  private readonly baseUrl = `${environment.apiUrl}/reminders`;

  constructor(private readonly http: HttpClient) {}

  getReminders(): Observable<ApiResponse<ReminderResponse[]>> {
    return this.http.get<ApiResponse<ReminderResponse[]>>(this.baseUrl);
  }

  createReminder(request: CreateReminderRequest): Observable<ApiResponse<ReminderResponse>> {
    return this.http.post<ApiResponse<ReminderResponse>>(this.baseUrl, request);
  }

  completeReminder(id: number): Observable<ApiResponse<ReminderResponse>> {
    return this.http.post<ApiResponse<ReminderResponse>>(`${this.baseUrl}/${id}/complete`, {});
  }
}
