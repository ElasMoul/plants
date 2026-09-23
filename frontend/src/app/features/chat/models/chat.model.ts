export interface ChatMessageDto {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequest {
  language?: 'en' | 'fr' | 'ar';
  message: string;
  plantId?: number;
  history?: ChatMessageDto[];
}

export interface ChatResponse {
  reply: string;
}
