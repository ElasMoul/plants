package com.plantpal.session.service;

import com.plantpal.session.entity.RevokedReason;
import com.plantpal.session.entity.SessionCheckResult;
import java.time.Instant;

/**
 * The server-authoritative session registry (ADR-1). Owns the idle TTL, the absolute cap and
 * revocation — see SessionRegistryServiceImpl for the Redis-backed mechanics.
 */
public interface SessionRegistryService {

  /** Opens a registry record on login/register. Called after the JWT (carrying this jti) is issued. */
  void createSession(String jti, Long userId, Instant issuedAt, Instant absoluteExpiresAt);

  /**
   * The slide: renews the idle window for a qualifying authenticated request (D3 — callers must
   * only invoke this for genuine user-initiated activity, never for the non-renewing allowlist).
   */
  SessionCheckResult validateAndSlide(String jti, Instant absoluteExpiresAtFromToken);

  /** Read-only status peek — never renews. Backs GET /auth/session, which is itself non-renewing. */
  SessionCheckResult status(String jti, Instant absoluteExpiresAtFromToken);

  /** Explicit user-intent renewal (POST /auth/session/renew) — always slides, allowlist or not. */
  SessionCheckResult renew(String jti, Instant absoluteExpiresAtFromToken);

  /** Revokes one session (logout) or, with reason PASSWORD_CHANGE/ADMIN, every session of a user. */
  void revoke(String jti, RevokedReason reason);

  /** Revokes every live session belonging to a user — password change, account suspension. */
  void revokeAllForUser(Long userId, RevokedReason reason);
}
