package com.plantpal.user.entity;

public enum ReasoningModelPreference {
  DEEPSEEK_R1,
  GITHUB_O4_MINI,
  GITHUB_GPT41_MINI,
  OLLAMA_GEMMA3,
  ANTHROPIC_CLAUDE,

  /**
   * @deprecated superseded by {@link #OLLAMA_GEMMA3} (Ollama's recommended local model moved from
   *     llava-phi3 to gemma3). Kept so rows persisted before this change still parse; routing
   *     treats it identically to {@link #OLLAMA_GEMMA3}.
   */
  @Deprecated
  OLLAMA_LLAVA
}
