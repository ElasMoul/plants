package com.plantpal.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plantpal.identification.event.IdentificationCompletedEvent;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.species.repository.SpeciesRepository;
import com.plantpal.user.repository.UserRepository;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** New-content hooks only: no startup sweep, no changes to existing plant records. */
@Component
public class GeneratedTranslationListener {
  private static final Logger log = LoggerFactory.getLogger(GeneratedTranslationListener.class);
  private final UserRepository users;
  private final IdentificationRepository identifications;
  private final SpeciesRepository species;
  private final TranslationService translations;
  private final TranslationTextExtractor extractor;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;

  public GeneratedTranslationListener(
      UserRepository users,
      IdentificationRepository identifications,
      SpeciesRepository species,
      TranslationService translations,
      TranslationTextExtractor extractor,
      ObjectMapper mapper,
      JdbcTemplate jdbc) {
    this.users = users;
    this.identifications = identifications;
    this.species = species;
    this.translations = translations;
    this.extractor = extractor;
    this.mapper = mapper;
    this.jdbc = jdbc;
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onIdentification(IdentificationCompletedEvent event) {
    if (!"COMPLETED".equals(event.getStatus())) return;
    identifications
        .findById(event.getIdentificationId())
        .ifPresent(
            scan -> {
              try {
                ObjectNode content = mapper.createObjectNode();
                content.put("commonName", scan.getCommonName());
                content.put("healthNotes", scan.getHealthNotes());
                content.set("carePlan", parse(scan.getCarePlan()));
                content.set("annotationRegions", parse(scan.getAnnotationRegions()));
                prepare(scan.getUserId(), content, false);
              } catch (Exception failed) {
                log.warn(
                    "Identification translation preparation failed: {}",
                    event.getIdentificationId());
              }
            });
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onGenerated(GeneratedPlantText event) {
    prepare(event.userId(), mapper.valueToTree(event.content()), false);
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onSpecies(SpeciesTextReady event) {
    // Only a species associated with an Arabic-preferring account's scan is eligible.
    var owners =
        jdbc.queryForList(
            """
        SELECT i.user_id FROM identifications i JOIN users u ON u.id=i.user_id
        WHERE i.species_id=? AND u.language='ar' ORDER BY i.id DESC LIMIT 1
        """,
            Long.class,
            event.speciesId());
    if (owners.isEmpty()) return;
    species
        .findById(event.speciesId())
        .ifPresent(
            value -> {
              try {
                ObjectNode content = mapper.createObjectNode();
                content.put("commonName", value.getCommonName());
                content.put("description", value.getDescription());
                content.put("careOverview", value.getCareOverview());
                content.set("careCards", parse(value.getCareCards()));
                prepare(owners.get(0), content, true);
              } catch (Exception failed) {
                log.warn("Species translation preparation failed: {}", event.speciesId());
              }
            });
  }

  private com.fasterxml.jackson.databind.JsonNode parse(String json) throws Exception {
    return json == null ? mapper.nullNode() : mapper.readTree(json);
  }

  private void prepare(
      Long userId, com.fasterxml.jackson.databind.JsonNode content, boolean shared) {
    try {
      if (!users.findById(userId).map(user -> "ar".equals(user.getLanguage())).orElse(false))
        return;
      var texts = new ArrayList<>(extractor.extract(content));
      texts.addAll(extractor.names(content));
      translations.prepare(texts.stream().distinct().sorted().toList(), userId, shared, "ar");
    } catch (RuntimeException failed) {
      log.warn(
          "Arabic translation preparation failed for user {}: {}",
          userId,
          failed.getClass().getSimpleName());
    }
  }
}
