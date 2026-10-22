package com.plantpal.mail.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.EmailTemplateRenderer;
import com.plantpal.mail.dto.EmailDeliveryView;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.mapper.EmailDeliveryMapper;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.mail.service.MailService;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailServiceImpl implements MailService {

  private final EmailOutboxRepository outbox;
  private final EmailTemplateRenderer renderer;
  private final EmailDeliveryMapper mapper;
  private final ObjectMapper objectMapper;

  public MailServiceImpl(
      EmailOutboxRepository outbox,
      EmailTemplateRenderer renderer,
      EmailDeliveryMapper mapper,
      ObjectMapper objectMapper) {
    this.outbox = outbox;
    this.renderer = renderer;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public Long send(
      EmailTemplate template, String to, String language, Map<String, String> variables) {
    if (to == null || to.isBlank()) {
      throw new IllegalArgumentException("Email recipient is required");
    }
    // Render once now so a bad template/variable set fails the caller immediately instead of
    // surfacing later as a dead row in the outbox.
    renderer.render(template, language, variables);

    EmailOutbox row = new EmailOutbox();
    row.setTemplate(template);
    row.setRecipient(to.trim());
    row.setLanguage(language == null ? "en" : language);
    row.setVariables(toJson(variables));
    row.setNextAttemptAt(Instant.now());
    return outbox.save(row).getId();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<EmailDeliveryView> deliveries(EmailStatus status, Pageable pageable) {
    Page<EmailOutbox> page =
        status == null ? outbox.findAll(pageable) : outbox.findByStatus(status, pageable);
    return page.map(mapper::toView);
  }

  private String toJson(Map<String, String> variables) {
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Email variables are not serializable", e);
    }
  }
}
