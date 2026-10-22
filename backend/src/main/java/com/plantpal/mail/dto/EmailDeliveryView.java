package com.plantpal.mail.dto;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.entity.EmailStatus;
import java.time.Instant;

/** Operator-facing outcome of one queued email. Never carries the message body or its links. */
public record EmailDeliveryView(
    Long id,
    EmailTemplate template,
    String recipient,
    String language,
    EmailStatus status,
    int attempts,
    Instant nextAttemptAt,
    String lastError,
    Instant sentAt,
    Instant createdAt) {}
