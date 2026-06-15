export type AiModelPreference = 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA';

export interface UserPreferences {
  aiModelPreference: AiModelPreference;
}

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface AuthTokenPayload {
  sub: string;
  userId: number;
  email: string;
  exp: number;
  iat: number;
}
