package com.plantpal.session.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The server-authoritative record of one signed-in session (architecture pack's Session entity) —
 * NOT a JPA entity. It lives entirely in Redis, keyed by the JWT's jti, because native key TTL
 * gives the sliding idle window for free and losing the store on restart is a safe failure for a
 * session store (everyone signs back in), not data loss.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRecord {

  private String jti;
  private Long userId;
  private Instant issuedAt;
  private Instant lastSeenAt;
  private Instant absoluteExpiresAt;
  private int idleTtlSeconds;
}
