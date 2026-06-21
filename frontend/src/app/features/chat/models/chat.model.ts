export interface ChatMessageDto {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequest {
  message: string;
  plantId?: number;
  history?: ChatMessageDto[];
}

export interface ChatResponse {
  reply: string;
}
