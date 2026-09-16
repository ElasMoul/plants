package com.plantpal.session.entity;

import lombok.Getter;

/**
 * The outcome of asking the registry "is this jti still a live session" — either from a
 * validate-and-slide check (renews the idle window) or a read-only status peek (does not).
 * {@code valid=false} always carries a {@link RevokedReason}; the registry infers
 * IDLE_TIMEOUT vs ABSOLUTE_CAP for a naturally-expired session (see
 * SessionRegistryServiceImpl's class Javadoc for why a TTL eviction can't leave its own
 * tombstone) and reads the explicit reason back for a revoked one.
 */
@Getter
public class SessionCheckResult {

  private final boolean valid;
  private final Long secondsRemaining;
  private final Long absoluteSecondsRemaining;
  private final RevokedReason revokedReason;

  private SessionCheckResult(
      boolean valid, Long secondsRemaining, Long absoluteSecondsRemaining, RevokedReason reason) {
    this.valid = valid;
    this.secondsRemaining = secondsRemaining;
    this.absoluteSecondsRemaining = absoluteSecondsRemaining;
    this.revokedReason = reason;
  }

  public static SessionCheckResult active(long secondsRemaining, long absoluteSecondsRemaining) {
    return new SessionCheckResult(true, secondsRemaining, absoluteSecondsRemaining, null);
  }

  public static SessionCheckResult invalid(RevokedReason reason) {
    return new SessionCheckResult(false, null, null, reason);
  }
}
