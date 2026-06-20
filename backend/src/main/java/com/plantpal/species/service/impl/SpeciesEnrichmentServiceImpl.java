package com.plantpal.species.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.species.entity.Species;
import com.plantpal.species.entity.SpeciesStatus;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.species.service.SpeciesEnrichmentService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fire-and-forget — called from {@code SpeciesServiceImpl.findOrCreate()} with no caller waiting on
 * the result, so {@link #enrich} must never throw and must never block the identification flow that
 * triggered it.
 */
@Service
public class SpeciesEnrichmentServiceImpl implements SpeciesEnrichmentService {

  private static final Logger log = LoggerFactory.getLogger(SpeciesEnrichmentServiceImpl.class);

  private static final String AI_SOURCE = "AI";

  private final SpeciesRepository speciesRepository;
  private final DeepSeekClient deepSeekClient;
  private final ObjectMapper objectMapper;

  public SpeciesEnrichmentServiceImpl(
      SpeciesRepository speciesRepository,
      DeepSeekClient deepSeekClient,
      ObjectMapper objectMapper) {
    this.speciesRepository = speciesRepository;
    this.deepSeekClient = deepSeekClient;
    this.objectMapper = objectMapper;
  }

  @Override
  @Async("aiTaskExecutor")
  @Transactional
  public void enrich(Long speciesId) {
    Species species = speciesRepository.findById(speciesId).orElse(null);
    if (species == null) {
      log.warn("Species not found for enrichment, skipping: id={}", speciesId);
      return;
    }

    try {
      String raw =
          deepSeekClient.generateSpeciesEnrichment(
              species.getScientificName(), species.getCommonName());
      SpeciesEnrichmentJson parsed = objectMapper.readValue(raw, SpeciesEnrichmentJson.class);

      species.setDescription(parsed.getDescription());
      species.setCareOverview(parsed.getCareOverview());
      species.setImageUrl(parsed.getImageUrl());
      species.setExternalDataSource(AI_SOURCE);
      species.setExternalDataFetchedAt(Instant.now());
      speciesRepository.save(species);
      log.info("Species enrichment succeeded: id={}", speciesId);

    } catch (Exception e) {
      log.warn(
          "Species enrichment failed, flipping to NEEDS_REVIEW: id={}, error={}",
          speciesId,
          e.getMessage());
      species.setStatus(SpeciesStatus.NEEDS_REVIEW);
      speciesRepository.save(species);
    }
  }

  /**
   * Wire shape returned by {@link DeepSeekClient#generateSpeciesEnrichment}. {@code source} is
   * part of the AI response schema but deliberately unused — {@link #enrich} always hardcodes
   * {@link #AI_SOURCE} on success rather than trusting the model's echoed value; the field is
   * still declared so deserialization doesn't depend on the caller's {@link ObjectMapper} having
   * {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled (Spring Boot's auto-configured bean disables it,
   * but a bare {@code new ObjectMapper()} — e.g. in a unit test — does not).
   */
  private static final class SpeciesEnrichmentJson {
    private String description;
    private String careOverview;
    private String imageUrl;
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

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }
}
