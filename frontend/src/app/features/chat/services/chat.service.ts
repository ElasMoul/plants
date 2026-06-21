import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
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
    return this.http.post<ApiResponse<ChatResponse>>(this.baseUrl, this.buildRequest(message, plantId, history));
  }

  // responseType: 'text' + reportProgress + observe: 'events' lets us read
  // HttpDownloadProgressEvent.partialText as SSE chunks arrive, incrementally -- using
  // HttpClient (not a raw fetch()) keeps the JWT auth interceptor working on this request too.
  sendMessageStream(
    message: string,
    plantId?: number,
    history?: ChatMessageDto[],
  ): Observable<HttpEvent<string>> {
    return this.http.post(this.baseUrl + '/stream', this.buildRequest(message, plantId, history), {
      responseType: 'text',
      reportProgress: true,
      observe: 'events',
    });
  }

  private buildRequest(message: string, plantId?: number, history?: ChatMessageDto[]): ChatRequest {
    const request: ChatRequest = { message };
    if (plantId != null) request.plantId = plantId;
    if (history != null && history.length) request.history = history;
    return request;
  }
}
