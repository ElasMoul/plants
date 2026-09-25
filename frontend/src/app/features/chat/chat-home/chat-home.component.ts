import { translate } from '../../../shared/i18n/language.service';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpDownloadProgressEvent, HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ChatService } from '../services/chat.service';
import { PlantService } from '../../plant/services/plant.service';
import { ChatMessageDto } from '../models/chat.model';
import { AiErrorService } from '../../../core/services/ai-error.service';
import { SseParser } from '@plantpal/shared-core';

interface ChatMessage {
  id: number;
  sender: 'user' | 'ai';
  text: string;
  // Set when the AI reply was blocked by the platform ceiling (HTTP 402) — the thread renders a
  // dedicated block notice instead of the text bubble (platform D023: never a generic error).
  blocked?: boolean;
  blockReason?: string | null;
  // The reply failed (error text or "no reply" notice shown instead) — never sent back as history.
  failed?: boolean;
}

// The canned opening greeting isn't a real conversation turn — excluded from history sent to the AI.
const GREETING_ID = 1;

@Component({
    selector: 'app-chat-home',
    templateUrl: './chat-home.component.html',
    styleUrls: ['./chat-home.component.scss'],
    standalone: false
})
export class ChatHomeComponent implements OnInit, OnDestroy {
  readonly quickChips: string[] = [
    translate('Why are my leaves yellow?'),
    translate('How often should I water?'),
    translate('Best light for Monstera?'),
  ];

  messages: ChatMessage[] = [
    {
      id: 1,
      sender: 'ai',
      text: translate("Hi! I'm your PlantPal AI assistant. Ask me anything about your garden — watering, light, pests, or diagnosing issues."),
    },
  ];

  draft = '';
  sending = false;

  contextPlantId: number | null = null;
  contextPlantNickname: string | null = null;

  private nextId = 2;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly chatService: ChatService,
    private readonly plantService: PlantService,
    private readonly route: ActivatedRoute,
    private readonly aiErrorService: AiErrorService,
  ) {}

  ngOnInit(): void {
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const plantId = Number(params['plantId']);
      if (params['plantId'] && !Number.isNaN(plantId)) {
        this.setContextPlant(plantId);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  clearContext(): void {
    this.contextPlantId = null;
    this.contextPlantNickname = null;
  }

  private setContextPlant(plantId: number): void {
    this.contextPlantId = plantId;
    this.plantService.getPlant(plantId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.contextPlantNickname = res.data.nickname;
        },
        error: () => {
          // Plant lookup failed — keep the plantId threaded into requests, just skip the chip.
        },
      });
  }

  sendMessage(): void {
    const text = this.draft.trim();
    if (!text || this.sending) return;

    const history = this.buildHistory();
    this.messages.push({ id: this.nextId++, sender: 'user', text });
    this.draft = '';
    this.sending = true;

    const aiMessageId = this.nextId++;
    this.messages.push({ id: aiMessageId, sender: 'ai', text: '' });

    // Reads the cumulative partialText incrementally, exactly as Spring frames tokens: multi-line
    // tokens are rejoined with their newlines and a token's own leading space is kept.
    const parser = new SseParser();

    this.chatService.sendMessageStream(text, this.contextPlantId ?? undefined, history)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: event => {
          if (event.type === HttpEventType.DownloadProgress) {
            const progress = event as HttpDownloadProgressEvent;
            parser.feed(progress.partialText ?? '').forEach(token => this.appendToAiMessage(aiMessageId, token));
          } else if (event.type === HttpEventType.Response) {
            parser.end().forEach(token => this.appendToAiMessage(aiMessageId, token));
            this.sending = false;
            const msg = this.messages.find(m => m.id === aiMessageId);
            if (msg && !msg.text) {
              msg.text = translate("Sorry, I didn't get a reply. Please try again.");
              msg.failed = true;
            }
          }
        },
        error: (err: HttpErrorResponse) => {
          this.sending = false;
          const msg = this.messages.find(m => m.id === aiMessageId);
          if (!msg) return;
          // A 402 ceiling block gets the explicit block state, not a generic error line. The input
          // stays usable (sending=false) so the user isn't dead-ended.
          if (this.aiErrorService.isBlocked(err)) {
            msg.blocked = true;
            msg.blockReason = this.aiErrorService.blockReason(err);
          } else {
            msg.text = this.aiErrorService.handle(err);
            msg.failed = true;
          }
        },
      });
  }

  private appendToAiMessage(id: number, token: string): void {
    const msg = this.messages.find(m => m.id === id);
    if (msg) msg.text += token;
  }

  // Excludes the canned greeting and the not-yet-pushed current-turn message -- this is called
  // before that message is pushed, so "all messages so far" is exactly the prior turns. Failed or
  // blocked replies are UI notices, not things the assistant said, so they stay out too.
  private buildHistory(): ChatMessageDto[] {
    return this.messages
      .filter(m => m.id !== GREETING_ID && !m.failed && !m.blocked)
      .map(m => ({ role: m.sender === 'ai' ? 'assistant' : 'user', content: m.text }));
  }

  useQuickChip(chip: string): void {
    this.draft = chip;
  }

  trackByMessageId(_index: number, message: ChatMessage): number {
    return message.id;
  }

}
