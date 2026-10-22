package com.plantpal.mail.sender;

import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev/test transport: delivers nothing. The body is never logged because it carries one-time links;
 * only the subject and a masked recipient are.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

  @Override
  public void send(EmailMessage message) {
    if (log.isInfoEnabled()) {
      log.info(
          "Email not delivered (app.mail.transport=log): to={} subject=\"{}\"",
          MailAddresses.mask(message.to()),
          message.subject());
    }
  }
}
