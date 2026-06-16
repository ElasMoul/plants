import { Component } from '@angular/core';

interface ChatMessage {
  id: number;
  sender: 'user' | 'ai';
  text: string;
  probability?: number;
  careTip?: string;
}

@Component({
  selector: 'app-chat-home',
  templateUrl: './chat-home.component.html',
  styleUrls: ['./chat-home.component.scss'],
})
export class ChatHomeComponent {
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
    {
      id: 2,
      sender: 'user',
      text: 'Why does Monty have yellow spots on a few leaves?',
    },
    {
      id: 3,
      sender: 'ai',
      text: 'Yellow spots on Monstera leaves are most often caused by overwatering or inconsistent watering. It can also signal early-stage leaf spot disease if the spots have dark, defined edges.',
      probability: 82,
      careTip: 'Let the top 2-3cm of soil dry out fully between waterings, and make sure the pot drains well.',
    },
  ];

  draft = '';

  sendMessage(): void {
    const text = this.draft.trim();
    if (!text) {
      return;
    }
    this.messages.push({ id: this.messages.length + 1, sender: 'user', text });
    this.draft = '';
  }

  useQuickChip(chip: string): void {
    this.draft = chip;
  }
}
