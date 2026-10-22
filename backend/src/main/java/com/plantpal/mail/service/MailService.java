package com.plantpal.mail.service;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.dto.EmailDeliveryView;
import com.plantpal.mail.entity.EmailStatus;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The single way the application sends email. Verification and recovery both call {@link #send};
 * nothing else touches a transport.
 */
public interface MailService {

  /**
   * Queues an email. Joins the caller's transaction, so the email exists if and only if the
   * caller's change commits; delivery, retries and failure tracking happen afterwards.
   *
   * @param variables template placeholders; every {@link EmailTemplate#REQUIRED_VARIABLES} entry
   *     must be present, and {@code link} must be an http(s) URL
   * @throws IllegalArgumentException if the variables cannot fill the template
   * @return the outbox id
   */
  Long send(EmailTemplate template, String to, String language, Map<String, String> variables);

  /** Operator view of the outbox; pass {@code null} for every status. */
  Page<EmailDeliveryView> deliveries(EmailStatus status, Pageable pageable);
}
