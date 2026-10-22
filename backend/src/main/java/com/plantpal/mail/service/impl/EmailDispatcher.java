package com.plantpal.mail.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.EmailSender;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.EmailTemplateRenderer;
import com.plantpal.mail.MailProperties;
import com.plantpal.mail.RenderedEmail;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.mail.sender.MailAddresses;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends queued emails and owns the retry policy. Each run claims a small batch under a row lock (so
 * several instances never send the same email), pushes each message through the {@link
 * EmailSender}, and records the outcome. A failed send is retried with exponential backoff; after
 * {@code app.mail.max-attempts} the row becomes FAILED, an ERROR is logged (it reaches Sentry) and
 * the {@code plantpal.mail.delivery{outcome=failed}} counter is incremented.
 *
 * <p>Delivery is at-least-once: if the process dies after the provider accepted a message but
 * before the row is marked SENT, the claim lease expires and the email is sent again.
 */
@Component
public class EmailDispatcher {

  private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final TypeReference<Map<String, String>> VARIABLES_TYPE = new TypeReference<>() {};
  private static final String METRIC = "plantpal.mail.delivery";
  private static final String OUTCOME_TAG = "outcome";

  private final EmailOutboxRepository outbox;
  private final EmailSender sender;
  private final EmailTemplateRenderer renderer;
  private final MailProperties properties;
  private final TransactionTemplate tx;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meters;

  public EmailDispatcher(
      EmailOutboxRepository outbox,
      EmailSender sender,
      EmailTemplateRenderer renderer,
      MailProperties properties,
      TransactionTemplate tx,
      ObjectMapper objectMapper,
      MeterRegistry meters) {
    this.outbox = outbox;
    this.sender = sender;
    this.renderer = renderer;
    this.properties = properties;
    this.tx = tx;
    this.objectMapper = objectMapper;
    this.meters = meters;
  }

  @Scheduled(fixedDelayString = "${app.mail.poll-interval:PT10S}")
  public void dispatchDue() {
    dispatchDue(Instant.now());
  }

  /** Visible for tests: runs one dispatch cycle as of {@code now}; returns how many were tried. */
  int dispatchDue(Instant now) {
    List<Claimed> claimed = tx.execute(status -> claim(now));
    if (claimed == null) {
      return 0;
    }
    claimed.forEach(this::deliver);
    return claimed.size();
  }

  private List<Claimed> claim(Instant now) {
    List<EmailOutbox> due = outbox.lockDue(now, properties.batchSize());
    for (EmailOutbox row : due) {
      row.setAttempts(row.getAttempts() + 1);
      // Lease: if this instance dies mid-send, the row simply becomes due again later.
      row.setNextAttemptAt(now.plus(properties.claimLease()));
    }
    return due.stream().map(Claimed::of).toList();
  }

  private void deliver(Claimed claim) {
    try {
      Map<String, String> variables = objectMapper.readValue(claim.variables(), VARIABLES_TYPE);
      RenderedEmail email = renderer.render(claim.template(), claim.language(), variables);
      sender.send(
          new EmailMessage(claim.recipient(), email.subject(), email.textBody(), email.htmlBody()));
    } catch (IllegalArgumentException | JsonProcessingException e) {
      // The content itself is broken; sending it again cannot help.
      recordFailure(claim, e, true);
      return;
    } catch (RuntimeException e) {
      recordFailure(claim, e, false);
      return;
    }
    recordSent(claim);
  }

  private void recordSent(Claimed claim) {
    tx.executeWithoutResult(
        status ->
            outbox
                .findById(claim.id())
                .ifPresent(
                    row -> {
                      row.setStatus(EmailStatus.SENT);
                      row.setSentAt(Instant.now());
                      row.setLastError(null);
                      row.setVariables("{}");
                    }));
    meters.counter(METRIC, OUTCOME_TAG, "sent").increment();
    log.info(
        "Email sent: id={} template={} to={} attempts={}",
        claim.id(),
        claim.template(),
        claim.maskedRecipient(),
        claim.attempts());
  }

  private void recordFailure(Claimed claim, Exception error, boolean permanent) {
    String message =
        truncate(
            error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    boolean giveUp = permanent || claim.attempts() >= properties.maxAttempts();
    Instant retryAt = Instant.now().plus(backoff(claim.attempts()));
    tx.executeWithoutResult(
        status ->
            outbox
                .findById(claim.id())
                .ifPresent(
                    row -> {
                      row.setLastError(message);
                      if (giveUp) {
                        row.setStatus(EmailStatus.FAILED);
                        row.setVariables("{}");
                      } else {
                        row.setNextAttemptAt(retryAt);
                      }
                    }));
    if (giveUp) {
      meters.counter(METRIC, OUTCOME_TAG, "failed").increment();
      log.error(
          "Email delivery FAILED permanently: id={} template={} to={} attempts={} error={}",
          claim.id(),
          claim.template(),
          claim.maskedRecipient(),
          claim.attempts(),
          message);
    } else {
      meters.counter(METRIC, OUTCOME_TAG, "retry").increment();
      log.warn(
          "Email delivery failed, will retry: id={} template={} to={} attempt={}/{} retryAt={} error={}",
          claim.id(),
          claim.template(),
          claim.maskedRecipient(),
          claim.attempts(),
          properties.maxAttempts(),
          retryAt,
          message);
    }
  }

  /** 1x, 2x, 4x... the base delay: attempt 1 waits one base delay before attempt 2. */
  private java.time.Duration backoff(int attempts) {
    return properties.retryBaseDelay().multipliedBy(1L << Math.clamp(attempts - 1, 0, 10));
  }

  private static String truncate(String value) {
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }

  /** Plain copy of a claimed row so sending happens outside any transaction or lock. */
  private record Claimed(
      Long id,
      EmailTemplate template,
      String recipient,
      String maskedRecipient,
      String language,
      String variables,
      int attempts) {

    static Claimed of(EmailOutbox row) {
      return new Claimed(
          row.getId(),
          row.getTemplate(),
          row.getRecipient(),
          MailAddresses.mask(row.getRecipient()),
          row.getLanguage(),
          row.getVariables(),
          row.getAttempts());
    }
  }
}
