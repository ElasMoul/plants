package com.plantpal.mail.entity;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One email waiting to be sent, or the record of how it ended. Rows are never deleted. */
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "template", nullable = false, length = 40)
  private EmailTemplate template;

  @Column(name = "recipient", nullable = false, length = 255)
  private String recipient;

  @Column(name = "language", nullable = false, length = 8)
  private String language;

  /** JSON map of template placeholders; blanked ({@code {}}) once the row is SENT or FAILED. */
  @Column(name = "variables", nullable = false)
  private String variables;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private EmailStatus status = EmailStatus.PENDING;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;
}
