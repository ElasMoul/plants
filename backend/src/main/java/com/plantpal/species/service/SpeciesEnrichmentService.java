package com.plantpal.species.service;

import com.plantpal.user.entity.AiModelPreference;

/**
 * Fetches description/careOverview prose for a newly created Species row. Narrowed in T9.B —
 * imageUrl is no longer fetched here (identity + image now come from PlantNet at resolve time).
 * {@code SpeciesServiceImpl.findOrCreate} injects this as an {@code Optional} dependency — still
 * safe to leave unimplemented in tests/contexts where no bean is registered (degrades to a no-op).
 */
public interface SpeciesEnrichmentService {

  /**
   * @param preference the triggering user's chosen AI model — OLLAMA_LLAVA/OLLAMA_GEMMA3 routes
   *     through the local Ollama text completion; every other preference uses DeepSeekClient.
   */
  void enrich(Long speciesId, AiModelPreference preference);
}
