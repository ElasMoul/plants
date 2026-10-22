package com.plantpal.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.mail.EmailDeliveryException;
import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.EmailSender;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.EmailTemplateRenderer;
import com.plantpal.mail.MailProperties;
import com.plantpal.mail.MailProperties.Transport;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailDispatcher - Unit Tests")
class EmailDispatcherTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final String GOOD_VARIABLES =
      "{\"name\":\"Sara\",\"link\":\"https://www.planotell.com/verify?token=abc\"}";

  @Mock private EmailOutboxRepository outbox;
  @Mock private EmailSender sender;

  private SimpleMeterRegistry meters;
  private EmailDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    meters = new SimpleMeterRegistry();
    MailProperties properties =
        new MailProperties(
            Transport.SMTP,
            "Planotell <no-reply@example.com>",
            MAX_ATTEMPTS,
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            10);
    dispatcher =
        new EmailDispatcher(
            outbox,
            sender,
            new EmailTemplateRenderer(),
            properties,
            new TransactionTemplate(txManager),
            new ObjectMapper(),
            meters);
  }

  private EmailOutbox queued(int previousAttempts, String variables) {
    EmailOutbox row = new EmailOutbox();
    row.setId(1L);
    row.setTemplate(EmailTemplate.ACCOUNT_VERIFICATION);
    row.setRecipient("sara@example.com");
    row.setLanguage("fr");
    row.setVariables(variables);
    row.setAttempts(previousAttempts);
    row.setNextAttemptAt(Instant.now().minusSeconds(5));
    when(outbox.lockDue(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(row));
    when(outbox.findById(1L)).thenReturn(Optional.of(row));
    return row;
  }

  private double count(String outcome) {
    var counter = meters.find("plantpal.mail.delivery").tag("outcome", outcome).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  @DisplayName("sends a due email, marks it SENT and clears the one-time link")
  void sendsAndClearsVariables() {
    EmailOutbox row = queued(0, GOOD_VARIABLES);

    int tried = dispatcher.dispatchDue(Instant.now());

    ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
    verify(sender).send(sent.capture());
    assertThat(tried).isEqualTo(1);
    assertThat(sent.getValue().to()).isEqualTo("sara@example.com");
    assertThat(sent.getValue().textBody()).startsWith("Bonjour Sara,");
    assertThat(row.getStatus()).isEqualTo(EmailStatus.SENT);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getSentAt()).isNotNull();
    assertThat(row.getVariables()).isEqualTo("{}");
    assertThat(row.getLastError()).isNull();
    assertThat(count("sent")).isEqualTo(1);
  }

  @Test
  @DisplayName("does nothing when no email is due")
  void nothingDue() {
    when(outbox.lockDue(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

    assertThat(dispatcher.dispatchDue(Instant.now())).isZero();

    verify(sender, never()).send(any());
  }

  @Test
  @DisplayName("a failed send stays PENDING with the error recorded and is retried later")
  void retriesAfterFailure() {
    EmailOutbox row = queued(0, GOOD_VARIABLES);
    doThrow(new EmailDeliveryException("SMTP delivery failed: connection refused", null))
        .when(sender)
        .send(any());
    Instant before = Instant.now();

    dispatcher.dispatchDue(before);

    assertThat(row.getStatus()).isEqualTo(EmailStatus.PENDING);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLastError()).contains("connection refused");
    assertThat(row.getNextAttemptAt()).isAfter(before.plus(Duration.ofSeconds(50)));
    assertThat(row.getVariables()).isEqualTo(GOOD_VARIABLES);
    assertThat(count("retry")).isEqualTo(1);
    assertThat(count("failed")).isZero();
  }

  @Test
  @DisplayName("backoff doubles with each attempt")
  void backoffDoubles() {
    EmailOutbox row = queued(1, GOOD_VARIABLES);
    doThrow(new EmailDeliveryException("down", null)).when(sender).send(any());
    Instant before = Instant.now();

    dispatcher.dispatchDue(before);

    assertThat(row.getAttempts()).isEqualTo(2);
    assertThat(row.getNextAttemptAt())
        .isBetween(before.plus(Duration.ofSeconds(110)), before.plus(Duration.ofSeconds(150)));
  }

  @Test
  @DisplayName("after the last attempt the email is FAILED, counted, and no longer retried")
  void givesUpAfterMaxAttempts() {
    EmailOutbox row = queued(MAX_ATTEMPTS - 1, GOOD_VARIABLES);
    doThrow(new EmailDeliveryException("535 authentication failed", null)).when(sender).send(any());

    dispatcher.dispatchDue(Instant.now());

    assertThat(row.getStatus()).isEqualTo(EmailStatus.FAILED);
    assertThat(row.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(row.getLastError()).contains("535 authentication failed");
    assertThat(row.getVariables()).isEqualTo("{}");
    assertThat(count("failed")).isEqualTo(1);
    assertThat(count("retry")).isZero();
  }

  @Test
  @DisplayName(
      "an email whose content cannot be rendered fails at once without hitting the provider")
  void brokenContentFailsImmediately() {
    EmailOutbox row = queued(0, "{\"name\":\"Sara\"}");

    dispatcher.dispatchDue(Instant.now());

    verify(sender, never()).send(any());
    assertThat(row.getStatus()).isEqualTo(EmailStatus.FAILED);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLastError()).contains("link");
    assertThat(count("failed")).isEqualTo(1);
  }

  @Test
  @DisplayName("unreadable stored variables also fail at once")
  void corruptVariablesFailImmediately() {
    EmailOutbox row = queued(0, "not json");

    dispatcher.dispatchDue(Instant.now());

    verify(sender, never()).send(any());
    assertThat(row.getStatus()).isEqualTo(EmailStatus.FAILED);
  }

  @Test
  @DisplayName("claiming leases the row so another instance does not pick it up mid-send")
  void claimLeasesTheRow() {
    EmailOutbox row = queued(0, GOOD_VARIABLES);
    Instant now = Instant.now();
    AtomicReference<Instant> leaseWhileSending = new AtomicReference<>();
    doAnswer(
            call -> {
              leaseWhileSending.set(row.getNextAttemptAt());
              return null;
            })
        .when(sender)
        .send(any());

    dispatcher.dispatchDue(now);

    assertThat(leaseWhileSending.get()).isEqualTo(now.plus(Duration.ofMinutes(2)));
  }
}
