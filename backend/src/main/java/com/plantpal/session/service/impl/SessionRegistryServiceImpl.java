package com.plantpal.session.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.session.config.SessionProperties;
import com.plantpal.session.entity.RevokedReason;
import com.plantpal.session.entity.SessionCheckResult;
import com.plantpal.session.entity.SessionRecord;
import com.plantpal.session.service.SessionRegistryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed session registry (ADR-1). Two Redis shapes:
 *
 * <ul>
 *   <li>{@code session:<jti>} — the live {@link SessionRecord}, JSON, TTL = the idle window. Native
 *       key expiry <em>is</em> the sliding-window mechanism; nothing sweeps it.
 *   <li>{@code session:revoked:<jti>} — a short-lived tombstone written only by an explicit {@link
 *       #revoke}. A TTL eviction has no code path to run at the moment it fires, so a
 *       naturally-idle-timed-out session can't leave one — {@link #peekOrInfer} infers IDLE_TIMEOUT
 *       for "record simply gone, and not past the absolute cap" instead.
 * </ul>
 *
 * <p>{@code session:user:<userId>} tracks live jti's per user (for {@link #revokeAllForUser} on
 * password change) with a rolling 7-day safety-net TTL so it can't grow unbounded; it may lag a
 * naturally-expired jti briefly, which is harmless — revoking an already-gone key is a no-op.
 */
@Service
public class SessionRegistryServiceImpl implements SessionRegistryService {

  private static final Logger log = LoggerFactory.getLogger(SessionRegistryServiceImpl.class);

  private static final String SESSION_KEY_PREFIX = "session:";
  private static final String REVOKED_KEY_PREFIX = "session:revoked:";
  private static final String USER_SESSIONS_KEY_PREFIX = "session:user:";
  private static final Duration TOMBSTONE_TTL = Duration.ofMinutes(5);
  private static final Duration USER_INDEX_TTL = Duration.ofDays(7);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SessionProperties properties;
  private final Clock clock;

  public SessionRegistryServiceImpl(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      SessionProperties properties,
      Clock clock) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public void createSession(String jti, Long userId, Instant issuedAt, Instant absoluteExpiresAt) {
    SessionRecord record =
        SessionRecord.builder()
            .jti(jti)
            .userId(userId)
            .issuedAt(issuedAt)
            .lastSeenAt(issuedAt)
            .absoluteExpiresAt(absoluteExpiresAt)
            .idleTtlSeconds(properties.getIdleTtlSeconds())
            .build();

    write(sessionKey(jti), record, Duration.ofSeconds(properties.getIdleTtlSeconds()));

    String userKey = userSessionsKey(userId);
    redisTemplate.opsForSet().add(userKey, jti);
    redisTemplate.expire(userKey, USER_INDEX_TTL);
  }

  @Override
  public SessionCheckResult validateAndSlide(String jti, Instant absoluteExpiresAtFromToken) {
    SessionCheckResult invalidResult =
        checkTombstoneAndAbsoluteCap(jti, absoluteExpiresAtFromToken);
    if (invalidResult != null) {
      return invalidResult;
    }

    SessionRecord record = read(sessionKey(jti));
    if (record == null) {
      return SessionCheckResult.invalid(RevokedReason.IDLE_TIMEOUT);
    }

    record =
        SessionRecord.builder()
            .jti(record.getJti())
            .userId(record.getUserId())
            .issuedAt(record.getIssuedAt())
            .lastSeenAt(clock.instant())
            .absoluteExpiresAt(record.getAbsoluteExpiresAt())
            .idleTtlSeconds(properties.getIdleTtlSeconds())
            .build();
    write(sessionKey(jti), record, Duration.ofSeconds(properties.getIdleTtlSeconds()));

    return SessionCheckResult.active(
        properties.getIdleTtlSeconds(), secondsUntil(absoluteExpiresAtFromToken));
  }

  @Override
  public SessionCheckResult status(String jti, Instant absoluteExpiresAtFromToken) {
    SessionCheckResult invalidResult =
        checkTombstoneAndAbsoluteCap(jti, absoluteExpiresAtFromToken);
    if (invalidResult != null) {
      return invalidResult;
    }

    String key = sessionKey(jti);
    if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
      return SessionCheckResult.invalid(RevokedReason.IDLE_TIMEOUT);
    }

    Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    long secondsRemaining = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds : 0;
    return SessionCheckResult.active(secondsRemaining, secondsUntil(absoluteExpiresAtFromToken));
  }

  @Override
  public SessionCheckResult renew(String jti, Instant absoluteExpiresAtFromToken) {
    // Explicit user intent (the warning's "keep me signed in") always slides —
    // it is never subject to the non-renewing allowlist, by construction of
    // being its own dedicated endpoint (apiSketch's POST /auth/session/renew).
    return validateAndSlide(jti, absoluteExpiresAtFromToken);
  }

  @Override
  public void revoke(String jti, RevokedReason reason) {
    redisTemplate.delete(sessionKey(jti));
    redisTemplate.opsForValue().set(revokedKey(jti), reason.name(), TOMBSTONE_TTL);
  }

  @Override
  public void revokeAllForUser(Long userId, RevokedReason reason) {
    String userKey = userSessionsKey(userId);
    Set<String> jtis = redisTemplate.opsForSet().members(userKey);
    if (jtis != null) {
      for (String jti : jtis) {
        revoke(jti, reason);
      }
    }
    redisTemplate.delete(userKey);
    log.info(
        "Revoked all sessions for userId={}, reason={}, count={}",
        userId,
        reason,
        jtis == null ? 0 : jtis.size());
  }

  /**
   * Returns a non-null invalid result if the tombstone exists or the absolute cap has passed; null
   * if neither applies (caller should keep checking).
   */
  private SessionCheckResult checkTombstoneAndAbsoluteCap(
      String jti, Instant absoluteExpiresAtFromToken) {
    String tombstone = redisTemplate.opsForValue().get(revokedKey(jti));
    if (tombstone != null) {
      return SessionCheckResult.invalid(parseReason(tombstone));
    }

    if (!clock.instant().isBefore(absoluteExpiresAtFromToken)) {
      redisTemplate.delete(sessionKey(jti));
      redisTemplate
          .opsForValue()
          .set(revokedKey(jti), RevokedReason.ABSOLUTE_CAP.name(), TOMBSTONE_TTL);
      return SessionCheckResult.invalid(RevokedReason.ABSOLUTE_CAP);
    }

    return null;
  }

  private RevokedReason parseReason(String raw) {
    try {
      return RevokedReason.valueOf(raw);
    } catch (IllegalArgumentException e) {
      log.warn("Unrecognized revocation reason in tombstone: {}", raw);
      return RevokedReason.ADMIN;
    }
  }

  private long secondsUntil(Instant target) {
    long seconds = Duration.between(clock.instant(), target).getSeconds();
    return Math.max(seconds, 0);
  }

  private void write(String key, SessionRecord record, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(record), ttl);
    } catch (Exception e) {
      log.warn("Failed to write session record for key={}: {}", key, e.getMessage());
    }
  }

  private SessionRecord read(String key) {
    String raw = redisTemplate.opsForValue().get(key);
    if (raw == null) {
      return null;
    }
    try {
      return objectMapper.readValue(raw, SessionRecord.class);
    } catch (Exception e) {
      log.warn("Failed to deserialize session record for key={}: {}", key, e.getMessage());
      return null;
    }
  }

  private String sessionKey(String jti) {
    return SESSION_KEY_PREFIX + jti;
  }

  private String revokedKey(String jti) {
    return REVOKED_KEY_PREFIX + jti;
  }

  private String userSessionsKey(Long userId) {
    return USER_SESSIONS_KEY_PREFIX + userId;
  }
}
