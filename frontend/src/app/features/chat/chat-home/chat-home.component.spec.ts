import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { frameToken } from '@plantpal/shared-core';
import { AiErrorService } from '../../../core/services/ai-error.service';
import { PlantService } from '../../plant/services/plant.service';
import { ChatService } from '../services/chat.service';
import { ChatHomeComponent } from './chat-home.component';

describe('ChatHomeComponent', () => {
  let stream: Subject<unknown>;
  let chat: { sendMessageStream: jest.Mock };
  let plants: { getPlant: jest.Mock };
  let aiError: { isBlocked: jest.Mock; blockReason: jest.Mock; handle: jest.Mock };
  let queryParams: BehaviorSubject<Record<string, string>>;
  let component: ChatHomeComponent;

  beforeEach(() => {
    stream = new Subject();
    chat = { sendMessageStream: jest.fn(() => stream) };
    plants = { getPlant: jest.fn(() => of({ data: { nickname: 'Monty' } })) };
    aiError = {
      isBlocked: jest.fn(() => false),
      blockReason: jest.fn(() => 'Daily AI limit reached'),
      handle: jest.fn(() => 'The assistant is busy — try again shortly'),
    };
    queryParams = new BehaviorSubject<Record<string, string>>({});
    component = new ChatHomeComponent(
      chat as unknown as ChatService,
      plants as unknown as PlantService,
      { queryParams } as unknown as ActivatedRoute,
      aiError as unknown as AiErrorService,
    );
    component.ngOnInit();
  });

  afterEach(() => component.ngOnDestroy());

  /** Streams the given tokens framed exactly as Spring's SseEmitter writes them. */
  const streamTokens = (tokens: string[], cuts: number[] = []) => {
    const wire = tokens.map(frameToken).join('');
    const ends = [...cuts, wire.length];
    for (const end of ends) {
      stream.next({ type: HttpEventType.DownloadProgress, loaded: end, partialText: wire.slice(0, end) });
    }
  };
  const finish = () => {
    stream.next({ type: HttpEventType.Response });
    stream.complete();
  };
  const lastAi = () => component.messages[component.messages.length - 1];
  const send = (text: string) => {
    component.draft = text;
    component.sendMessage();
  };

  it('rebuilds the reply byte-exactly: paragraph breaks and leading spaces survive', () => {
    send('Why are my leaves yellow?');
    const tokens = ['Most', ' likely', ' overwatering.', '\n\n', 'Let the soil', ' dry out.'];

    streamTokens(tokens, [7, 23, 40]); // chunk boundaries fall mid-line
    finish();

    expect(lastAi().text).toBe('Most likely overwatering.\n\nLet the soil dry out.');
    expect(component.sending).toBe(false);
  });

  it('keeps a token that was still unterminated when the stream ended', () => {
    send('hi');
    stream.next({ type: HttpEventType.DownloadProgress, loaded: 12, partialText: 'data:Hello th' });
    stream.next({ type: HttpEventType.DownloadProgress, loaded: 15, partialText: 'data:Hello there' });
    finish();

    expect(lastAi().text).toBe('Hello there');
  });

  it('an empty reply shows a retry notice that is not sent back as history', () => {
    send('first');
    finish();
    expect(lastAi().text).toContain("didn't get a reply");

    send('second');
    const history = chat.sendMessageStream.mock.calls[1][2];
    expect(history).toEqual([{ role: 'user', content: 'first' }]);
  });

  it('an error shows the AI error text, a 402 shows the block notice; neither enters history', () => {
    chat.sendMessageStream.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    send('one');
    expect(component.messages[2].text).toBe('The assistant is busy — try again shortly');

    aiError.isBlocked.mockReturnValue(true);
    chat.sendMessageStream.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 402 })));
    send('two');
    expect(component.messages[4].blocked).toBe(true);
    expect(component.messages[4].blockReason).toBe('Daily AI limit reached');
    expect(component.sending).toBe(false);

    send('three');
    expect(chat.sendMessageStream.mock.calls[2][2]).toEqual([
      { role: 'user', content: 'one' },
      { role: 'user', content: 'two' },
    ]);
  });

  it('sends prior successful turns as history, excluding the greeting', () => {
    send('Hi');
    streamTokens(['Hello!']);
    finish();

    send('Thanks');

    expect(chat.sendMessageStream.mock.calls[1][2]).toEqual([
      { role: 'user', content: 'Hi' },
      { role: 'assistant', content: 'Hello!' },
    ]);
  });

  it('ignores blank drafts and double-sends while a reply is streaming', () => {
    send('   ');
    expect(chat.sendMessageStream).not.toHaveBeenCalled();

    send('one');
    send('two');
    expect(chat.sendMessageStream).toHaveBeenCalledTimes(1);
  });

  it('threads a plant from the query string into requests and shows its nickname', () => {
    queryParams.next({ plantId: '42' });
    expect(component.contextPlantId).toBe(42);
    expect(component.contextPlantNickname).toBe('Monty');

    send('How is it doing?');
    expect(chat.sendMessageStream.mock.calls[0][1]).toBe(42);

    component.clearContext();
    expect(component.contextPlantId).toBeNull();
  });

  it('ignores a non-numeric plantId and fills the draft from a quick chip', () => {
    queryParams.next({ plantId: 'abc' });
    expect(plants.getPlant).not.toHaveBeenCalled();

    component.useQuickChip(component.quickChips[0]);
    expect(component.draft).toBe(component.quickChips[0]);
    expect(component.trackByMessageId(0, component.messages[0])).toBe(1);
  });
});
