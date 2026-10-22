package com.plantpal.journal.entity;

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

/** A dated event in a plant's life, owned by the user who owns the plant. */
@Entity
@Table(name = "plant_journal_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantJournalEvent extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plant_id", nullable = false)
  private Long plantId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private JournalEventType type;

  @Column(name = "event_date", nullable = false)
  private LocalDate eventDate;

  @Column(name = "note", length = 1000)
  private String note;
}
