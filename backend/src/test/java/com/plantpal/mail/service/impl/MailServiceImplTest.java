package com.plantpal.mail.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.EmailTemplateRenderer;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.mapper.EmailDeliveryMapperImpl;
import com.plantpal.mail.repository.EmailOutboxRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("MailServiceImpl - Unit Tests")
class MailServiceImplTest {

  private static final Map<String, String> VARS =
      Map.of("name", "Sara", "link", "https://www.planotell.com/verify?token=abc");

  @Mock private EmailOutboxRepository outbox;

  private MailServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new MailServiceImpl(
            outbox, new EmailTemplateRenderer(), new EmailDeliveryMapperImpl(), new ObjectMapper());
  }

  @Test
  @DisplayName("send() queues a PENDING row that is due immediately")
  void queuesPendingRow() {
    when(outbox.save(any(EmailOutbox.class)))
        .thenAnswer(
            call -> {
              EmailOutbox saved = call.getArgument(0);
              saved.setId(42L);
              return saved;
            });

    Long id = service.send(EmailTemplate.ACCOUNT_VERIFICATION, " sara@example.com ", "fr", VARS);

    ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
    verify(outbox).save(captor.capture());
    EmailOutbox row = captor.getValue();
    assertThat(id).isEqualTo(42L);
    assertThat(row.getRecipient()).isEqualTo("sara@example.com");
    assertThat(row.getLanguage()).isEqualTo("fr");
    assertThat(row.getTemplate()).isEqualTo(EmailTemplate.ACCOUNT_VERIFICATION);
    assertThat(row.getStatus()).isEqualTo(EmailStatus.PENDING);
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getNextAttemptAt()).isNotNull();
    assertThat(row.getVariables()).contains("\"name\":\"Sara\"").contains("token=abc");
  }

  @Test
  @DisplayName("send() defaults a missing language to English")
  void defaultsLanguage() {
    when(outbox.save(any(EmailOutbox.class))).thenAnswer(call -> call.getArgument(0));

    service.send(EmailTemplate.ACCOUNT_RECOVERY, "sara@example.com", null, VARS);

    ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
    verify(outbox).save(captor.capture());
    assertThat(captor.getValue().getLanguage()).isEqualTo("en");
  }

  @Test
  @DisplayName("send() rejects a blank recipient without queueing")
  void rejectsBlankRecipient() {
    assertThatThrownBy(() -> service.send(EmailTemplate.ACCOUNT_RECOVERY, " ", "en", VARS))
        .isInstanceOf(IllegalArgumentException.class);

    verify(outbox, never()).save(any());
  }

  @Test
  @DisplayName("send() fails fast on unusable variables instead of queueing a dead row")
  void rejectsBadVariables() {
    assertThatThrownBy(
            () ->
                service.send(
                    EmailTemplate.ACCOUNT_RECOVERY,
                    "sara@example.com",
                    "en",
                    Map.of("name", "Sara")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("link");

    verify(outbox, never()).save(any());
  }

  @Test
  @DisplayName("deliveries() lists everything without a status and filters with one")
  void listsDeliveries() {
    Pageable page = PageRequest.of(0, 20);
    EmailOutbox failed = new EmailOutbox();
    failed.setId(7L);
    failed.setTemplate(EmailTemplate.ACCOUNT_RECOVERY);
    failed.setRecipient("sara@example.com");
    failed.setLanguage("en");
    failed.setStatus(EmailStatus.FAILED);
    failed.setLastError("boom");
    when(outbox.findAll(page)).thenReturn(new PageImpl<>(java.util.List.of(failed), page, 1));
    when(outbox.findByStatus(EmailStatus.FAILED, page))
        .thenReturn(new PageImpl<>(java.util.List.of(failed), page, 1));

    Page<?> all = service.deliveries(null, page);
    Page<?> onlyFailed = service.deliveries(EmailStatus.FAILED, page);

    assertThat(all.getTotalElements()).isEqualTo(1);
    assertThat(onlyFailed.getContent()).hasSize(1);
    verify(outbox).findAll(page);
    verify(outbox).findByStatus(EmailStatus.FAILED, page);
  }
}
