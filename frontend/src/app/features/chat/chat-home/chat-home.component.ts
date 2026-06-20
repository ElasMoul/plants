import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ChatService } from '../services/chat.service';
import { PlantService } from '../../plant/services/plant.service';

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
export class ChatHomeComponent implements OnInit, OnDestroy {
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

  contextPlantId: number | null = null;
  contextPlantNickname: string | null = null;

  private nextId = 2;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly chatService: ChatService,
    private readonly plantService: PlantService,
    private readonly route: ActivatedRoute,
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

    this.messages.push({ id: this.nextId++, sender: 'user', text });
    this.draft = '';
    this.sending = true;

    this.chatService.sendMessage(text, this.contextPlantId ?? undefined)
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
