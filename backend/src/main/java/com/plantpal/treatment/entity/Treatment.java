package com.plantpal.treatment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A single disease's own treatment lifecycle for one plant — NOT the same concept as {@link
 * com.plantpal.reminder.entity.TreatmentPlan} (a generic multi-step action plan backed by Reminder
 * rows). {@code Treatment} wraps a {@code TreatmentPlan} once {@code craftPlan()} runs rather than
 * duplicating its step/reminder storage. Same no-audit-columns exception as Reminder and
 * TreatmentPlan — uses {@code @CreationTimestamp}/{@code @UpdateTimestamp} instead.
 */
@Entity
@Table(name = "treatments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Treatment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plant_id", nullable = false)
  private Long plantId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "identification_id")
  private Long identificationId;

  @Column(name = "disease_name", nullable = false, length = 255)
  private String diseaseName;

  @Column(name = "disease_description", columnDefinition = "TEXT")
  private String diseaseDescription;

  @Column(name = "disease_description_model", length = 30)
  private String diseaseDescriptionModel;

  @Column(name = "treatment_plan_model", length = 30)
  private String treatmentPlanModel;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TreatmentStatus status = TreatmentStatus.DRAFT;

  @Column(name = "treatment_plan_id")
  private Long treatmentPlanId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "plantnet_second_opinion", columnDefinition = "jsonb")
  private String plantnetSecondOpinion;

  @Column(name = "plantnet_agreement")
  private Boolean plantnetAgreement;

  @Column(name = "eppo_code", length = 50)
  private String eppoCode;

  @Builder.Default
  @Column(name = "needs_review", nullable = false)
  private boolean needsReview = false;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
