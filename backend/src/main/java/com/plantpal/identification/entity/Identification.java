package com.plantpal.identification.entity;

import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "identifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Identification extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plant_id")
  private Long plantId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "photo_url")
  private String photoUrl;

  @Column(name = "raw_response", columnDefinition = "TEXT")
  private String rawResponse;

  @Column(name = "scientific_name", length = 255)
  private String scientificName;

  @Column(name = "common_name", length = 255)
  private String commonName;

  @Column(name = "confidence")
  private Double confidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private IdentificationStatus status;

  @Column(name = "health_status", length = 30)
  private String healthStatus;

  @Column(name = "health_notes", columnDefinition = "TEXT")
  private String healthNotes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "care_plan", columnDefinition = "jsonb")
  private String carePlan;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "annotation_regions", columnDefinition = "jsonb")
  private String annotationRegions;

  @Column(name = "source_image_width")
  private Integer sourceImageWidth;

  @Column(name = "source_image_height")
  private Integer sourceImageHeight;

  @Column(name = "ai_model_used", length = 30)
  private String aiModelUsed;

  @Column(name = "species_id")
  private Long speciesId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "plantnet_candidates", columnDefinition = "jsonb")
  private String plantnetCandidates;

  @Column(name = "plantnet_version", length = 50)
  private String plantnetVersion;

  @Column(name = "plantnet_best_match", length = 255)
  private String plantnetBestMatch;

  @Column(name = "plantnet_switch_to_project", length = 50)
  private String plantnetSwitchToProject;

  @Column(name = "plantnet_quota_remaining")
  private Integer plantnetQuotaRemaining;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "plantnet_disease_results", columnDefinition = "jsonb")
  private String plantnetDiseaseResults;

  @Column(name = "plantnet_disease_quota_remaining")
  private Integer plantnetDiseaseQuotaRemaining;

  // Phase 8.5 T8.A — per-stage status + model tracking (migration 027)

  @Enumerated(EnumType.STRING)
  @Column(name = "identification_status", nullable = false, length = 30)
  private IdentificationStageStatus identificationStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "annotation_status", nullable = false, length = 30)
  private IdentificationStageStatus annotationStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "candidate_status", nullable = false, length = 30)
  private IdentificationStageStatus candidateStatus;

  /** The VisionModelPreference enum name used for the core identification call. */
  @Column(name = "identification_model", length = 50)
  private String identificationModel;

  /** Always "gpt-4o-mini" today; captured for the Phase 9 eval corpus. */
  @Column(name = "annotation_model", length = 50)
  private String annotationModel;

  /** Structured failure tag when identification_status = FAILED: RATE_LIMITED | PARSE_ERROR | PROVIDER_ERROR | OTHER. */
  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;
}
