export type AiModelPreference = 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA' | 'GITHUB_GPT4O';

// Split out from AiModelPreference (T7.1/T7.2) — vision (identification + annotation) and
// reasoning (care plans, cure advice, disease description, species enrichment) are now
// independent choices. PLANTNET is dropped from the vision set (dead code, per BACKEND.md).
export type VisionModelPreference = 'GITHUB_GPT4O' | 'OLLAMA_LLAVA';
export type ReasoningModelPreference = 'DEEPSEEK_R1' | 'OLLAMA_LLAVA';

export interface UserPreferences {
  // Deprecated by the vision/reasoning split below — backend keeps the column for now (T7.1),
  // not read by any UI built after this point.
  aiModelPreference: AiModelPreference;
  visionModelPreference: VisionModelPreference;
  reasoningModelPreference: ReasoningModelPreference;
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
