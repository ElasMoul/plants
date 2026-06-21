import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../core/models/api-response.model';
import { ChatMessageDto, ChatRequest, ChatResponse } from '../models/chat.model';

@Injectable()
export class ChatService {
  private readonly baseUrl = `${environment.apiUrl}/chat`;

  constructor(private readonly http: HttpClient) {}

  sendMessage(
    message: string,
    plantId?: number,
    history?: ChatMessageDto[],
  ): Observable<ApiResponse<ChatResponse>> {
    const request: ChatRequest = { message };
    if (plantId != null) request.plantId = plantId;
    if (history != null && history.length) request.history = history;
    return this.http.post<ApiResponse<ChatResponse>>(this.baseUrl, request);
  }
}
