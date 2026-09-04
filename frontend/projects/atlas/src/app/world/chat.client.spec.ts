import {
  HttpErrorResponse,
  HttpEventType,
  provideHttpClient,
  type HttpDownloadProgressEvent,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideSharedCore } from '@plantpal/shared-core';
import { SettingsStore } from '../settings/settings.store';
import { ChatClient, classify, type ChatEvent } from './chat.client';
import { frameToken } from './sse-parse';
import type { ChatRequestDto, ChatTurnDto } from './world.dto';

// The chat sources are read as text below; jest runs on CommonJS, and the atlas
// carries no node typings, so the two names it needs are declared here.
declare const __dirname: string;
declare function require(id: string): { readFileSync(path: string, encoding: string): string };


function setup(): { client: ChatClient; http: HttpTestingController; settings: SettingsStore } {
  localStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      ...provideSharedCore({ apiBaseUrl: '/api/v1' }),
    ],
  });
  return {
    client: TestBed.inject(ChatClient),
    http: TestBed.inject(HttpTestingController),
    settings: TestBed.inject(SettingsStore),
  };
}

function turn(n: number): ChatTurnDto {
  return {
    id: `t${n}`,
    askedAt: `2026-09-04T09:0${n}:00.000Z`,
    question: `q${n}`,
    reply: `a${n}`,
    outcome: 'answered',
  };
}

/** One DownloadProgress event, as HttpClient builds it for a text response. */
function progress(partialText: string): HttpDownloadProgressEvent {
  return { type: HttpEventType.DownloadProgress, loaded: partialText.length, partialText };
}

describe('ChatClient', () => {
  it('unwraps the buffered endpoint into one token and a done', () => {
    const { client, http, settings } = setup();
    settings.set('ai.chatTransport', 'buffered');
    const seen: ChatEvent[] = [];
    client.ask({ question: 'why?' }).subscribe(e => seen.push(e));
    const req = http.expectOne('/api/v1/chat');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, data: { reply: 'Because.' } });
    expect(seen).toEqual([{ kind: 'token', text: 'Because.' }, { kind: 'done' }]);
    http.verify();
  });

  it('reads the streamed endpoint token by token and ends on the response', () => {
    const { client, http } = setup();
    const seen: ChatEvent[] = [];
    client.ask({ question: 'why?', plantId: 2 }).subscribe(e => seen.push(e));
    const req = http.expectOne('/api/v1/chat/stream');
    expect((req.request.body as ChatRequestDto).plantId).toBe(2);
    expect(req.request.responseType).toBe('text');
    expect(req.request.reportProgress).toBe(true);

    let text = frameToken('Draught,');
    req.event(progress(text));
    text += frameToken(' most likely.');
    req.event(progress(text));
    req.flush(text);

    expect(seen).toEqual([
      { kind: 'token', text: 'Draught,' },
      { kind: 'token', text: ' most likely.' },
      { kind: 'done' },
    ]);
    http.verify();
  });

  it('sends exactly the configured number of prior turns, oldest first', () => {
    const { client, http, settings } = setup();
    const turns = [turn(1), turn(2), turn(3), turn(4), turn(5), turn(6)];
    settings.set('ai.chatHistoryTurns', 2);
    client.ask({ question: 'and now?', history: turns }).subscribe();
    const body = http.expectOne('/api/v1/chat/stream').request.body as ChatRequestDto;
    expect(body.history).toEqual([
      { role: 'user', content: 'q5' },
      { role: 'assistant', content: 'a5' },
      { role: 'user', content: 'q6' },
      { role: 'assistant', content: 'a6' },
    ]);
    expect(client.history(turns)).toHaveLength(4);
    settings.set('ai.chatHistoryTurns', 0);
    expect(client.history(turns)).toEqual([]);
    settings.set('ai.chatHistoryTurns', 5);
    expect(client.history(turns)).toHaveLength(10);
  });

  it('never hands a truncated half-sentence back as the model’s own answer', () => {
    const { client, settings } = setup();
    settings.set('ai.chatHistoryTurns', 5);
    const cut = { ...turn(2), reply: 'the low leaves are', outcome: 'truncated' as const };
    expect(client.history([turn(1), cut, turn(3)])).toEqual([
      { role: 'user', content: 'q1' },
      { role: 'assistant', content: 'a1' },
      { role: 'user', content: 'q3' },
      { role: 'assistant', content: 'a3' },
    ]);
  });

  it('sends no plant when the reader asked for no plant context', () => {
    const { client, http, settings } = setup();
    settings.set('ai.chatPlantContext', 'never');
    client.ask({ question: 'why?', plantId: 2 }).subscribe();
    expect((http.expectOne('/api/v1/chat/stream').request.body as ChatRequestDto).plantId)
      .toBeUndefined();
    settings.set('ai.chatPlantContext', 'focused');
    client.ask({ question: 'why?', plantId: 2 }).subscribe();
    expect((http.expectOne('/api/v1/chat/stream').request.body as ChatRequestDto).plantId).toBe(2);
  });

  it('stores no empty answer: a reply-less 200 is a token-less done', () => {
    const { client, http, settings } = setup();
    settings.set('ai.chatTransport', 'buffered');
    const seen: ChatEvent[] = [];
    client.ask({ question: 'why?' }).subscribe(e => seen.push(e));
    http.expectOne('/api/v1/chat').flush({ success: true, data: null });
    expect(seen).toEqual([{ kind: 'done' }]);
    http.verify();
  });

  it('caps the message at the server’s own 2000 characters', () => {
    const { client, http } = setup();
    client.ask({ question: 'x'.repeat(2500) }).subscribe();
    const body = http.expectOne('/api/v1/chat/stream').request.body as ChatRequestDto;
    expect(body.message).toHaveLength(2000);
  });

  it('turns a failure into one failed event and closes', () => {
    const { client, http } = setup();
    const seen: ChatEvent[] = [];
    let closed = false;
    client.ask({ question: 'why?' }).subscribe({ next: e => seen.push(e), complete: () => (closed = true) });
    http
      .expectOne('/api/v1/chat/stream')
      .flush('{"success":false,"message":"Chat rate limit reached — try again later","errorCode":429}', {
        status: 429,
        statusText: 'Too Many Requests',
      });
    expect(seen).toEqual([{ kind: 'failed', failure: { kind: 'rate-limited', retryAfterSeconds: null } }]);
    expect(closed).toBe(true);
  });

  it('stops asking anything of the transport once unsubscribed', () => {
    const { client, http } = setup();
    const seen: ChatEvent[] = [];
    const sub = client.ask({ question: 'why?' }).subscribe(e => seen.push(e));
    const req = http.expectOne('/api/v1/chat/stream');
    req.event(progress(frameToken('Draught,')));
    sub.unsubscribe();
    expect(seen).toEqual([{ kind: 'token', text: 'Draught,' }]);
    expect(req.cancelled).toBe(true);
    http.verify();
  });
});

describe('classify', () => {
  const of = (status: number, error: unknown = null) =>
    new HttpErrorResponse({ status, error, url: '/api/v1/chat' });

  it('names every failure the chat endpoints can produce', () => {
    expect(classify(of(429)).kind).toBe('rate-limited');
    expect(classify(of(503)).kind).toBe('unavailable');
    expect(classify(of(0)).kind).toBe('offline');
    expect(classify(of(400)).kind).toBe('too-long');
    expect(classify(of(404)).kind).toBe('not-found');
    expect(classify(of(402)).kind).toBe('blocked');
    expect(classify(of(500)).kind).toBe('unknown');
    expect(classify(new Error('not http')).kind).toBe('unknown');
  });

  it('invents no wait: chat’s 429 carries no retryAfterSeconds', () => {
    expect(classify(of(429, { errorCode: 429 })).retryAfterSeconds).toBeNull();
    // Another endpoint's RateLimitException does carry one; it is used when it comes.
    expect(classify(of(429, { retryAfterSeconds: 900 })).retryAfterSeconds).toBe(900);
  });

  it('parses the error body a text response delivers as a string', () => {
    expect(classify(of(429, '{"retryAfterSeconds":60}')).retryAfterSeconds).toBe(60);
    expect(classify(of(503, 'data:half an answer')).kind).toBe('unavailable');
  });
});

describe('the chat code’s transport', () => {
  it('uses neither fetch nor EventSource anywhere', () => {
    const fs = require('fs');
    for (const file of ['chat.client.ts', 'chat.store.ts', 'sse-parse.ts']) {
      const source = fs.readFileSync(`${__dirname}/${file}`, 'utf8');
      // The doc comments name them to say they are not used; the code must not call them.
      const code = source.replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '');
      expect(code).not.toMatch(/\bfetch\s*\(/);
      expect(code).not.toMatch(/EventSource/);
    }
  });
});
