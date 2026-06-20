export interface ChatRequest {
  message: string;
  plantId?: number;
}

export interface ChatResponse {
  reply: string;
}
