import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api-response.model';
import { ChatRequest, ChatResponse } from '../models/chat.model';

@Injectable()
export class ChatService {
  private readonly baseUrl = `${environment.apiUrl}/chat`;

  constructor(private readonly http: HttpClient) {}

  sendMessage(message: string): Observable<ApiResponse<ChatResponse>> {
    const request: ChatRequest = { message };
    return this.http.post<ApiResponse<ChatResponse>>(this.baseUrl, request);
  }
}
