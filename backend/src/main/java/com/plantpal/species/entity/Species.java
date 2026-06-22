package com.plantpal.species.entity;

import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared botanical knowledge — NOT user-scoped. Two users who both own a Monstera deliciosa point
 * at the same row. Never add a userId/ownership check directly on this entity; ownership checks
 * belong on the Plant rows that reference it.
 */
@Entity
@Table(name = "species")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Species extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(name = "scientific_name", nullable = false, unique = true, length = 255)
  private String scientificName;

  @Column(name = "common_name", length = 255)
  private String commonName;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "care_overview", columnDefinition = "TEXT")
  private String careOverview;

  @Column(name = "image_url", columnDefinition = "TEXT")
  private String imageUrl;

  // Raw JSON, List<CareCardDto>-shaped (no actionPlan -- that needs plant-specific context like
  // reminders, which doesn't exist at the species level). Parsed on read in SpeciesServiceImpl,
  // same pattern as Identification.carePlan.
  @Column(name = "care_cards", columnDefinition = "TEXT")
  private String careCards;

  @Column(name = "external_data_source", length = 20)
  private String externalDataSource;

  // Which reasoning model produced description/careOverview/careCards above.
  @Column(name = "enrichment_model", length = 30)
  private String enrichmentModel;

  @Column(name = "external_data_fetched_at")
  private Instant externalDataFetchedAt;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private SpeciesStatus status = SpeciesStatus.ACTIVE;
}
