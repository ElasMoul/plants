package com.plantpal.mail.sender;

import com.plantpal.mail.EmailDeliveryException;
import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.EmailSender;
import com.plantpal.mail.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Real delivery over SMTP (Resend in production). Credentials come from {@code spring.mail.*}. */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  public SmtpEmailSender(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      MimeMessage mime = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
      helper.setFrom(properties.from());
      helper.setTo(message.to());
      helper.setSubject(message.subject());
      helper.setText(message.textBody(), message.htmlBody());
      mailSender.send(mime);
    } catch (MessagingException | MailException e) {
      throw new EmailDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
    }
  }
}
