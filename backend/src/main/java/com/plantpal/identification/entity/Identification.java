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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "care_plan", columnDefinition = "jsonb")
  private String carePlan;
}
