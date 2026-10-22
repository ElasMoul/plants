package com.plantpal.mail.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.plantpal.mail.EmailDeliveryException;
import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.MailProperties;
import com.plantpal.mail.MailProperties.Transport;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@DisplayName("SmtpEmailSender - real SMTP round trip (GreenMail)")
class SmtpEmailSenderTest {

  @RegisterExtension
  static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final MailProperties PROPERTIES =
      new MailProperties(
          Transport.SMTP,
          "Planotell <no-reply@example.com>",
          5,
          Duration.ofMinutes(1),
          Duration.ofMinutes(2),
          10);

  private SmtpEmailSender senderOn(int port) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost("localhost");
    mailSender.setPort(port);
    mailSender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
    mailSender.getJavaMailProperties().put("mail.smtp.timeout", "1000");
    return new SmtpEmailSender(mailSender, PROPERTIES);
  }

  @Test
  @DisplayName("delivers a multipart text+HTML message with the configured sender")
  void deliversMultipartMessage() throws Exception {
    senderOn(GREEN_MAIL.getSmtp().getPort())
        .send(
            new EmailMessage(
                "sara@example.com",
                "Confirmez votre adresse e-mail pour Planotell",
                "Bonjour Sara, lien : https://www.planotell.com/v",
                "<p>Bonjour <b>Sara</b></p>"));

    MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage message = received[0];
    assertThat(message.getSubject()).isEqualTo("Confirmez votre adresse e-mail pour Planotell");
    assertThat(message.getAllRecipients()[0]).hasToString("sara@example.com");
    assertThat(message.getFrom()[0].toString())
        .contains("no-reply@example.com")
        .contains("Planotell");
    String raw = GreenMailUtil.getWholeMessage(message);
    assertThat(raw).contains("multipart/alternative").contains("text/plain").contains("text/html");
  }

  @Test
  @DisplayName("wraps a connection failure in EmailDeliveryException so the dispatcher can retry")
  void wrapsConnectionFailure() {
    SmtpEmailSender unreachable = senderOn(1);
    EmailMessage message = new EmailMessage("sara@example.com", "s", "t", "<p>h</p>");

    assertThatThrownBy(() -> unreachable.send(message))
        .isInstanceOf(EmailDeliveryException.class)
        .hasMessageContaining("SMTP delivery failed");
  }

  @Test
  @DisplayName("wraps an invalid recipient address too")
  void wrapsInvalidRecipient() {
    SmtpEmailSender sender = senderOn(GREEN_MAIL.getSmtp().getPort());
    EmailMessage message = new EmailMessage("not an address", "s", "t", "<p>h</p>");

    assertThatThrownBy(() -> sender.send(message)).isInstanceOf(EmailDeliveryException.class);
  }
}
