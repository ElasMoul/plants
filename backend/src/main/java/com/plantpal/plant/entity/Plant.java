package com.plantpal.plant.entity;

import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plant extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "nickname", nullable = false, length = 255)
  private String nickname;

  @Column(name = "species", length = 255)
  private String species;

  @Column(name = "common_name", length = 255)
  private String commonName;

  @Column(name = "photo_url")
  private String photoUrl;

  @Column(name = "location", length = 100)
  private String location;

  @Column(name = "notes")
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PlantStatus status;

  @Column(name = "acquired_at")
  private LocalDate acquiredAt;
}
