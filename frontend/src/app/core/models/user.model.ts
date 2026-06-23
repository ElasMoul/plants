export type AiModelPreference = 'DEEPSEEK' | 'PLANTNET' | 'OLLAMA_LLAVA' | 'GITHUB_GPT4O';

// Split out from AiModelPreference (T7.1/T7.2) — vision (identification + annotation) and
// reasoning (care plans, cure advice, disease description, species enrichment) are now
// independent choices. PLANTNET restored as a vision choice — PlantNetClient was never actually
// removed backend-side, only dropped from this picker (see ARCHITECT.md).
// T8.0: expanded to 5 options each + Claude (ANTHROPIC_CLAUDE serves both menus — Claude is
// multimodal). OLLAMA_LLAVA kept only so older stored preference rows still type-check; the
// picker no longer offers it — OLLAMA_GEMMA3 is its replacement.
export type VisionModelPreference =
  | 'GITHUB_GPT4O'
  | 'GITHUB_GPT41'
  | 'OLLAMA_GEMMA3'
  | 'PLANTNET'
  | 'ANTHROPIC_CLAUDE'
  | 'OLLAMA_LLAVA';
export type ReasoningModelPreference =
  | 'DEEPSEEK_R1'
  | 'GITHUB_O4_MINI'
  | 'GITHUB_GPT41_MINI'
  | 'OLLAMA_GEMMA3'
  | 'ANTHROPIC_CLAUDE'
  | 'OLLAMA_LLAVA';

export interface UserPreferences {
  // Deprecated by the vision/reasoning split below — backend keeps the column for now (T7.1),
  // not read by any UI built after this point.
  aiModelPreference: AiModelPreference;
  visionModelPreference: VisionModelPreference;
  reasoningModelPreference: ReasoningModelPreference;
  // Keyed by enum name -> whether that option is actually usable right now (e.g. Claude needs an
  // API key configured server-side). Optional so cached pre-T8.0 sessionStorage entries don't
  // break — ModelSelectorComponent treats a missing entry as available.
  visionModelAvailability?: Partial<Record<VisionModelPreference, boolean>>;
  reasoningModelAvailability?: Partial<Record<ReasoningModelPreference, boolean>>;
  // PlantNet flora (project code e.g. "k-world-flora") and common-name language (T8.4).
  plantnetProject?: string;
  plantnetLang?: string;
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
