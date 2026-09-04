import {
  HttpClient,
  HttpErrorResponse,
  HttpEventType,
  type HttpDownloadProgressEvent,
} from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, ApiResponse } from '@plantpal/shared-core';
import { catchError, from, Observable, of, switchMap } from 'rxjs';
import { SettingsStore } from '../settings/settings.store';
import {
  MAX_MESSAGE_CHARS,
  type ChatFailure,
  type ChatMessageDto,
  type ChatRequestDto,
  type ChatResponseDto,
  type ChatTurnDto,
} from './world.dto';
import { SseParser } from './sse-parse';

/** What one ask emits. `done` closes it; `failed` closes it too, with a reason. */
export type ChatEvent =
  | { kind: 'token'; text: string }
  | { kind: 'done' }
  | { kind: 'failed'; failure: ChatFailure };

export interface AskInput {
  question: string;
  plantId?: number;
  /** The thread so far, oldest first. Only the tail actually travels. */
  history?: ChatTurnDto[];
}

/**
 * The one way the atlas speaks to /api/v1/chat.
 *
 * Always HttpClient — never `fetch`, never `EventSource`. EventSource cannot
 * carry the Bearer header AtlasAuthInterceptor attaches (it would 401 live), and
 * a bare fetch would bypass both that interceptor and the mock seam, breaking
 * mock mode's promise that no request leaves the page.
 *
 * There is no cancel method: an ask is cancelled by unsubscribing, which is what
 * every abort trigger does. No retry is ever fired on the caller's behalf — a
 * retry is a question the reader did not ask.
 */
@Injectable({ providedIn: 'root' })
export class ChatClient {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly settings = inject(SettingsStore);

  ask(input: AskInput): Observable<ChatEvent> {
    const body: ChatRequestDto = {
      message: input.question.slice(0, MAX_MESSAGE_CHARS),
      plantId: input.plantId,
      history: this.history(input.history ?? []),
    };
    return this.settings.get('ai.chatTransport') === 'buffered'
      ? this.buffered(body)
      : this.streamed(body);
  }

  /** The tail of the thread, flattened oldest-first into user/assistant pairs. */
  history(turns: ChatTurnDto[]): ChatMessageDto[] {
    const kept = Number(this.settings.get('ai.chatHistoryTurns')) || 0;
    if (kept <= 0) return [];
    const out: ChatMessageDto[] = [];
    for (const turn of turns.slice(-kept)) {
      out.push({ role: 'user', content: turn.question });
      out.push({ role: 'assistant', content: turn.reply });
    }
    return out;
  }

  private buffered(body: ChatRequestDto): Observable<ChatEvent> {
    return this.http.post<ApiResponse<ChatResponseDto>>(`${this.base}/chat`, body).pipe(
      switchMap(res =>
        from<ChatEvent[]>([{ kind: 'token', text: res.data?.reply ?? '' }, { kind: 'done' }]),
      ),
      catchError((err: unknown) => of<ChatEvent>({ kind: 'failed', failure: classify(err) })),
    );
  }

  private streamed(body: ChatRequestDto): Observable<ChatEvent> {
    return new Observable<ChatEvent>(subscriber => {
      const parser = new SseParser();
      const sub = this.http
        .post(`${this.base}/chat/stream`, body, {
          observe: 'events',
          responseType: 'text',
          reportProgress: true,
        })
        .subscribe({
          next: event => {
            if (event.type === HttpEventType.DownloadProgress) {
              const text = (event as HttpDownloadProgressEvent).partialText ?? '';
              for (const token of parser.feed(text)) subscriber.next({ kind: 'token', text: token });
              return;
            }
            if (event.type === HttpEventType.Response) {
              const text = typeof event.body === 'string' ? event.body : '';
              for (const token of parser.feed(text)) subscriber.next({ kind: 'token', text: token });
              for (const token of parser.end()) subscriber.next({ kind: 'token', text: token });
              subscriber.next({ kind: 'done' });
              subscriber.complete();
            }
          },
          error: (err: unknown) => {
            subscriber.next({ kind: 'failed', failure: classify(err) });
            subscriber.complete();
          },
        });
      return () => sub.unsubscribe();
    });
  }
}

/** The error body, whether it arrived parsed or as the raw text a text response gives. */
function bodyOf(err: HttpErrorResponse): Record<string, unknown> {
  const raw = err.error;
  if (raw && typeof raw === 'object') return raw as Record<string, unknown>;
  if (typeof raw === 'string') {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') return parsed as Record<string, unknown>;
    } catch {
      /* a stream that failed mid-flight is not JSON, and that is fine */
    }
  }
  return {};
}

/**
 * What happened, in the companion's terms. A wait is named only when the body
 * actually carries one: chat's 429 is a plain PlantPalException, so it does not.
 */
export function classify(err: unknown): ChatFailure {
  if (!(err instanceof HttpErrorResponse)) return { kind: 'unknown', retryAfterSeconds: null };
  const seconds = bodyOf(err)['retryAfterSeconds'];
  const retryAfterSeconds = typeof seconds === 'number' && seconds > 0 ? seconds : null;
  switch (err.status) {
    case 429:
      return { kind: 'rate-limited', retryAfterSeconds };
    case 503:
      return { kind: 'unavailable', retryAfterSeconds: null };
    case 0:
      return { kind: 'offline', retryAfterSeconds: null };
    case 400:
      return { kind: 'too-long', retryAfterSeconds: null };
    case 404:
      return { kind: 'not-found', retryAfterSeconds: null };
    case 402:
      return { kind: 'blocked', retryAfterSeconds: null };
    default:
      return { kind: 'unknown', retryAfterSeconds: null };
  }
}
