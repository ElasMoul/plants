package com.plantpal.species.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.shared.entity.GenerationStatus;
import com.plantpal.species.entity.Species;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ReasoningModelPreference;
import io.platform.contracts.aigateway.AiRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fire-and-forget — called from {@code SpeciesServiceImpl.findOrCreate()} with no caller waiting.
 * T9.B narrowed scope: generates ONLY description + careOverview prose. imageUrl is no longer
 * fetched here — identity + image come from the confirmed PlantNet candidate at resolve time
 * (T9.A). {@link #enrich} must never throw and must never block the identification flow that
 * triggered it.
 */
@Service
public class SpeciesEnrichmentServiceImpl implements SpeciesEnrichmentService {

  private static final Logger log = LoggerFactory.getLogger(SpeciesEnrichmentServiceImpl.class);
  private static final String AI_SOURCE = "AI";

  private final SpeciesRepository speciesRepository;
  private final DeepSeekClient deepSeekClient;
  private final OllamaClient ollamaClient;
  private final AnthropicClient anthropicClient;
  private final ObjectMapper objectMapper;
  private final GatewayClient gatewayClient;
  private final GatewayProperties gatewayProperties;

  public SpeciesEnrichmentServiceImpl(
      SpeciesRepository speciesRepository,
      DeepSeekClient deepSeekClient,
      OllamaClient ollamaClient,
      AnthropicClient anthropicClient,
      ObjectMapper objectMapper,
      GatewayClient gatewayClient,
      GatewayProperties gatewayProperties) {
    this.speciesRepository = speciesRepository;
    this.deepSeekClient = deepSeekClient;
    this.ollamaClient = ollamaClient;
    this.anthropicClient = anthropicClient;
    this.objectMapper = objectMapper;
    this.gatewayClient = gatewayClient;
    this.gatewayProperties = gatewayProperties;
  }

  @Override
  @Async("aiTaskExecutor")
  @Transactional
  // T-DEPLOY.2: this async write is the actual staleness risk for the "species" cache —
  // SpeciesServiceImpl.getSpecies(id) may already be cached with a PENDING descriptionStatus
  // from right after creation; without this eviction the cached response would never pick up
  // the enrichment result (or the FAILED status) until the 10-minute TTL expires.
  @CacheEvict(value = "species", key = "#speciesId")
  public void enrich(Long speciesId, AiModelPreference preference) {
    Species species = speciesRepository.findById(speciesId).orElse(null);
    if (species == null) {
      log.warn("Species not found for enrichment, skipping: id={}", speciesId);
      return;
    }

    boolean useOllama = preference == AiModelPreference.OLLAMA_LLAVA;
    // GitHub Models (DeepSeek-R1's transport) was retired upstream — when Claude is keyed it
    // takes over the non-Ollama branch; DeepSeek remains only as the un-keyed fallback.
    boolean useAnthropic = !useOllama && anthropicClient.isAvailable();
    try {
      String raw = generateEnrichment(species, useOllama, useAnthropic);
      SpeciesEnrichmentJson parsed = objectMapper.readValue(raw, SpeciesEnrichmentJson.class);

      species.setDescription(parsed.getDescription());
      species.setCareOverview(parsed.getCareOverview());
      // imageUrl is intentionally NOT set here — image identity comes from PlantNet (T9.A).
      species.setCareCards(
          parsed.getCareCards() != null && !parsed.getCareCards().isEmpty()
              ? objectMapper.writeValueAsString(parsed.getCareCards())
              : null);
      if (species.getExternalDataSource() == null) {
        species.setExternalDataSource(AI_SOURCE);
      }
      species.setEnrichmentModel(
          (useOllama
                  ? ReasoningModelPreference.OLLAMA_LLAVA
                  : useAnthropic
                      ? ReasoningModelPreference.ANTHROPIC_CLAUDE
                      : ReasoningModelPreference.DEEPSEEK_R1)
              .name());
      species.setExternalDataFetchedAt(Instant.now());
      species.setDescriptionStatus(GenerationStatus.READY);
      speciesRepository.save(species);
      log.info("Species enrichment succeeded: id={}", speciesId);

    } catch (Exception e) {
      log.warn("Species enrichment failed: id={}, error={}", speciesId, e.getMessage());
      species.setDescriptionStatus(GenerationStatus.FAILED);
      speciesRepository.save(species);
    }
  }

  /**
   * Gateway routing (D022, Chunk 3): both branches (Ollama vs DeepSeek) are in scope. Species
   * enrichment is fire-and-forget and not tied to a triggering user (Species has no per-row
   * ownership — see CLAUDE.md), so {@code userId="system"} is used for the required contracts field
   * rather than threading a user id through {@link #enrich}.
   */
  private String generateEnrichment(Species species, boolean useOllama, boolean useAnthropic) {
    if (gatewayProperties.enabled()) {
      String userMessage =
          "Scientific name: "
              + species.getScientificName()
              + (species.getCommonName() != null
                  ? "\nCommon name: " + species.getCommonName()
                  : "");
      String modelHint =
          useOllama
              ? ollamaClient.getModel()
              : useAnthropic ? anthropicClient.getDefaultModel() : deepSeekClient.getModel();
      AiRequest request =
          new AiRequest()
              .prompt(userMessage)
              .modelHint(modelHint)
              .appId("plantpal")
              .userId("system")
              .putContextItem("systemPrompt", DeepSeekClient.SPECIES_ENRICHMENT_SYSTEM_PROMPT);
      return gatewayClient.request(request).getResult();
    }
    if (useOllama) {
      return ollamaClient.generateSpeciesEnrichment(
          species.getScientificName(), species.getCommonName());
    }
    if (useAnthropic) {
      return anthropicClient.generateSpeciesEnrichment(
          species.getScientificName(), species.getCommonName());
    }
    return deepSeekClient.generateSpeciesEnrichment(
        species.getScientificName(), species.getCommonName());
  }

  /**
   * Wire shape returned by {@link DeepSeekClient#generateSpeciesEnrichment}. {@code source} and
   * {@code imageUrl} are part of the AI response schema but deliberately unused here — imageUrl
   * comes from PlantNet (T9.A); source is hardcoded to "AI" on success rather than trusting the
   * model's echoed value.
   */
  private static final class SpeciesEnrichmentJson {
    private String description;
    private String careOverview;
    private String imageUrl;
    private List<CareCardDto> careCards;
    private String source;

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getCareOverview() {
      return careOverview;
    }

    public void setCareOverview(String careOverview) {
      this.careOverview = careOverview;
    }

    public String getImageUrl() {
      return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
      this.imageUrl = imageUrl;
    }

    public List<CareCardDto> getCareCards() {
      return careCards;
    }

    public void setCareCards(List<CareCardDto> careCards) {
      this.careCards = careCards;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }
}
