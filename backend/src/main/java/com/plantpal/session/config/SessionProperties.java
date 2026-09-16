package com.plantpal.session.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADR-8: the registry ships inert first. {@code enforcementEnabled=false} (the default) means
 * JwtAuthFilter still creates, slides and observes sessions on every qualifying request, but
 * never rejects one whose registry record is gone — that switch is wave 5's deliberate,
 * separately-authorized cutover, not a side effect of deploying this code.
 *
 * <p>{@code absoluteCapHours} is deliberately its own property rather than reusing {@code
 * app.jwt.expiration-ms} (D4/ADR-3 recommend converging the two — the signed JWT exp becoming
 * the true backstop a wiped Redis can't extend past — but that changes today's 24h token
 * lifetime for every session immediately on deploy, independent of the enforcement flag. That
 * convergence is left as an explicit wave-5 decision, not made silently here.)
 */
@Component
@ConfigurationProperties(prefix = "app.session")
@Getter
@Setter
public class SessionProperties {

  private int idleTtlSeconds = 1800;
  private int absoluteCapHours = 12;
  private boolean enforcementEnabled = false;
}
