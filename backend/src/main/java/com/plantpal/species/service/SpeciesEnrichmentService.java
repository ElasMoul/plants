package com.plantpal.species.service;

import com.plantpal.user.entity.AiModelPreference;

/**
 * Fetches description/careOverview/imageUrl for a newly created Species row. {@code
 * SpeciesServiceImpl.findOrCreate} injects this as an {@code Optional} dependency — still safe to
 * leave unimplemented in tests/contexts where no bean is registered (degrades to a no-op).
 */
public interface SpeciesEnrichmentService {

  /**
   * @param preference the triggering user's chosen AI model — OLLAMA_LLAVA routes through the local
   *     Ollama text completion; every other preference uses DeepSeekClient (none of
   *     DEEPSEEK/GITHUB_GPT4O/PLANTNET do general text generation outside DeepSeekClient in this
   *     codebase).
   */
  void enrich(Long speciesId, AiModelPreference preference);
}
