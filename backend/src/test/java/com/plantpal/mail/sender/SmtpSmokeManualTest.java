package com.plantpal.mail.sender;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.plantpal.mail.EmailMessage;
import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.EmailTemplateRenderer;
import com.plantpal.mail.MailProperties;
import com.plantpal.mail.MailProperties.Transport;
import com.plantpal.mail.RenderedEmail;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Manual check that the real provider accepts our mail (PLA-25 "a test email reaches real
 * mailboxes"). Skipped everywhere unless {@code MAIL_SMOKE_TO} is set, so CI never sends mail.
 *
 * <p>Run it with the {@code MAIL_*} variables from {@code backend/.env} exported:
 *
 * <pre>MAIL_SMOKE_TO=you@example.com mvn test -Dtest=SmtpSmokeManualTest -Djacoco.skip=true</pre>
 *
 * Sends the verification and recovery emails in EN and FR through {@link SmtpEmailSender}. The
 * credentials come from the environment only and are never logged.
 */
@EnabledIfEnvironmentVariable(named = "MAIL_SMOKE_TO", matches = ".+")
@DisplayName("SMTP smoke test against the real provider (manual)")
class SmtpSmokeManualTest {

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  @Test
  @DisplayName("sends all four templates to MAIL_SMOKE_TO")
  void sendsAllTemplates() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(env("MAIL_HOST", "smtp.resend.com"));
    mailSender.setPort(Integer.parseInt(env("MAIL_PORT", "587")));
    mailSender.setUsername(env("MAIL_USERNAME", "resend"));
    mailSender.setPassword(System.getenv("MAIL_PASSWORD"));
    var props = mailSender.getJavaMailProperties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.starttls.required", "true");
    props.put("mail.smtp.connectiontimeout", "10000");
    props.put("mail.smtp.timeout", "20000");
    props.put("mail.smtp.writetimeout", "20000");

    MailProperties properties =
        new MailProperties(
            Transport.SMTP,
            env("MAIL_FROM", "Planotell <no-reply@localhost>"),
            1,
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            1);
    SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties);
    EmailTemplateRenderer renderer = new EmailTemplateRenderer();
    String to = System.getenv("MAIL_SMOKE_TO");
    Map<String, String> vars =
        Map.of("name", "Planotell tester", "link", "https://www.planotell.com/");

    for (EmailTemplate template : EmailTemplate.values()) {
      for (String language : new String[] {"en", "fr"}) {
        RenderedEmail email = renderer.render(template, language, vars);
        assertThatCode(
                () ->
                    sender.send(
                        new EmailMessage(
                            to,
                            "[PLA-25 test] " + email.subject(),
                            email.textBody(),
                            email.htmlBody())))
            .as("%s/%s", template, language)
            .doesNotThrowAnyException();
      }
    }
  }
}
