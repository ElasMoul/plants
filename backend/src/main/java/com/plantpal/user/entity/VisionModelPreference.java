package com.plantpal.user.entity;

public enum VisionModelPreference {
  GITHUB_GPT4O,
  GITHUB_GPT41,
  OLLAMA_GEMMA3,
  PLANTNET,
  ANTHROPIC_CLAUDE,

  /**
   * @deprecated superseded by {@link #OLLAMA_GEMMA3} (Ollama's recommended local vision model
   *     moved from llava-phi3 to gemma3). Kept so rows persisted before this change still parse;
   *     routing treats it identically to {@link #OLLAMA_GEMMA3}.
   */
  @Deprecated
  OLLAMA_LLAVA
}
