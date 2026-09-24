package com.plantpal.localization;

import com.plantpal.identification.event.IdentificationCompletedEvent;
import com.plantpal.identification.repository.IdentificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GeneratedTranslationListener {
  private static final Logger log = LoggerFactory.getLogger(GeneratedTranslationListener.class);
  private final SectionTranslationService sections;
  private final IdentificationRepository scans;
  private final JdbcTemplate jdbc;

  public GeneratedTranslationListener(
      SectionTranslationService sections, IdentificationRepository scans, JdbcTemplate jdbc) {
    this.sections = sections;
    this.scans = scans;
    this.jdbc = jdbc;
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onIdentification(IdentificationCompletedEvent event) {
    if (!"COMPLETED".equals(event.getStatus())) return;
    scans
        .findById(event.getIdentificationId())
        .ifPresent(scan -> generate("scan", scan.getId(), scan.getUserId()));
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onGenerated(GeneratedPlantText event) {
    generate(event.kind(), event.resourceId(), event.userId(), event.section());
    if ("plan".equals(event.kind())) {
      var ids =
          jdbc.queryForList(
              "SELECT id FROM reminders WHERE treatment_plan_id=? AND user_id=? ORDER BY id LIMIT 300",
              Long.class,
              event.resourceId(),
              event.userId());
      for (Long id : ids) generate("step", id, event.userId());
    }
  }

  @Async("aiTaskExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onSpecies(SpeciesTextReady event) {
    var owners =
        jdbc.queryForList(
            "SELECT user_id FROM identifications WHERE species_id=? ORDER BY id DESC LIMIT 1",
            Long.class,
            event.speciesId());
    if (!owners.isEmpty()) generate("species", event.speciesId(), owners.get(0));
  }

  private void generate(String kind, Long id, Long userId) {
    generate(kind, id, userId, null);
  }

  private void generate(String kind, Long id, Long userId, String section) {
    try {
      sections.generated(kind, id, userId, section);
    } catch (RuntimeException e) {
      log.warn(
          "Content language preparation failed: {} {} ({})",
          kind,
          id,
          e.getClass().getSimpleName());
    }
  }
}
