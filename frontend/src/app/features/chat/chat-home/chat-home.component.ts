import { Component, OnDestroy } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ChatService } from '../services/chat.service';

interface ChatMessage {
  id: number;
  sender: 'user' | 'ai';
  text: string;
}

@Component({
  selector: 'app-chat-home',
  templateUrl: './chat-home.component.html',
  styleUrls: ['./chat-home.component.scss'],
})
export class ChatHomeComponent implements OnDestroy {
  readonly quickChips: string[] = [
    'Why are my leaves yellow?',
    'How often should I water?',
    'Best light for Monstera?',
  ];

  messages: ChatMessage[] = [
    {
      id: 1,
      sender: 'ai',
      text: "Hi! I'm your PlantPal AI assistant. Ask me anything about your garden — watering, light, pests, or diagnosing issues.",
    },
  ];

  draft = '';
  sending = false;

  private nextId = 2;
  private readonly destroy$ = new Subject<void>();

  constructor(private readonly chatService: ChatService) {}

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  sendMessage(): void {
    const text = this.draft.trim();
    if (!text || this.sending) return;

    this.messages.push({ id: this.nextId++, sender: 'user', text });
    this.draft = '';
    this.sending = true;

    this.chatService.sendMessage(text)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.messages.push({ id: this.nextId++, sender: 'ai', text: res.data.reply });
          this.sending = false;
        },
        error: (err: HttpErrorResponse) => {
          this.messages.push({ id: this.nextId++, sender: 'ai', text: this.mapError(err) });
          this.sending = false;
        },
      });
  }

  useQuickChip(chip: string): void {
    this.draft = chip;
  }

  private mapError(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return "I'm having trouble connecting — check your internet and try again.";
    }
    return 'Sorry, something went wrong on my end. Please try again in a moment.';
  }
}
