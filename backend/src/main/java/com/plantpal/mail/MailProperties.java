package com.plantpal.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Transactional email settings ({@code app.mail.*}). No credential lives here: the SMTP host, user
 * and password are the standard {@code spring.mail.*} properties, fed from environment variables.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
    @DefaultValue("log") Transport transport,
    @DefaultValue("Planotell <no-reply@localhost>") String from,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("PT1M") Duration retryBaseDelay,
    @DefaultValue("PT2M") Duration claimLease,
    @DefaultValue("10") int batchSize) {

  /** How a message leaves the process: {@code SMTP} for real delivery, {@code LOG} for dev/test. */
  public enum Transport {
    SMTP,
    LOG
  }
}
