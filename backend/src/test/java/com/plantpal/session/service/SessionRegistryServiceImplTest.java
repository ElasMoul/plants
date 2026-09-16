package com.plantpal.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plantpal.session.config.SessionProperties;
import com.plantpal.session.entity.RevokedReason;
import com.plantpal.session.entity.SessionCheckResult;
import com.plantpal.session.service.impl.SessionRegistryServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionRegistryServiceImpl - Unit Tests")
class SessionRegistryServiceImplTest {

  private static final String JTI = "11111111-1111-1111-1111-111111111111";
  private static final Long USER_ID = 7L;

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private SetOperations<String, String> setOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final SessionProperties properties = new SessionProperties();

  private Instant now;
  private Clock clock;
  private SessionRegistryServiceImpl service;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-09-14T12:00:00Z");
    clock = Clock.fixed(now, ZoneOffset.UTC);
    properties.setIdleTtlSeconds(1800);
    properties.setAbsoluteCapHours(12);
    service = new SessionRegistryServiceImpl(redisTemplate, objectMapper, properties, clock);
  }

  @Nested
  @DisplayName("createSession()")
  class CreateSession {
    @Test
    @DisplayName("writes the record with the configured idle TTL and indexes it under the user")
    void writesRecordAndIndexesUser() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(redisTemplate.opsForSet()).thenReturn(setOperations);

      service.createSession(JTI, USER_ID, now, now.plus(Duration.ofHours(12)));

      verify(valueOperations).set(eq("session:" + JTI), anyString(), eq(Duration.ofSeconds(1800)));
      verify(setOperations).add("session:user:" + USER_ID, JTI);
      verify(redisTemplate).expire(eq("session:user:" + USER_ID), eq(Duration.ofDays(7)));
    }
  }

  @Nested
  @DisplayName("validateAndSlide() — absolute cap boundary (D4, 12h cap)")
  class AbsoluteCapBoundary {
    @Test
    @DisplayName("one second before the absolute cap: still active")
    void oneSecondBeforeCap() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("session:revoked:" + JTI)).thenReturn(null);
      when(valueOperations.get("session:" + JTI))
          .thenReturn(recordJson(now.minusSeconds(60), now.plusSeconds(1)));

      SessionCheckResult result = service.validateAndSlide(JTI, now.plusSeconds(1));

      assertThat(result.isValid()).isTrue();
      verify(valueOperations).set(eq("session:" + JTI), anyString(), eq(Duration.ofSeconds(1800)));
    }

    @Test
    @DisplayName("exactly at / past the absolute cap: expired, tombstoned, and never re-slid")
    void atOrPastCap() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("session:revoked:" + JTI)).thenReturn(null);

      SessionCheckResult result = service.validateAndSlide(JTI, now);

      assertThat(result.isValid()).isFalse();
      assertThat(result.getRevokedReason()).isEqualTo(RevokedReason.ABSOLUTE_CAP);
      verify(redisTemplate).delete("session:" + JTI);
      verify(valueOperations)
          .set(eq("session:revoked:" + JTI), eq("ABSOLUTE_CAP"), eq(Duration.ofMinutes(5)));
      // Must not read the live record or slide it once the cap has passed.
      verify(valueOperations, never()).get("session:" + JTI);
      verify(valueOperations, never())
          .set(eq("session:" + JTI), anyString(), eq(Duration.ofSeconds(1800)));
    }
  }

  @Nested
  @DisplayName("validateAndSlide() — natural idle expiry")
  class IdleExpiry {
    @Test
    @DisplayName(
        "record naturally evicted by Redis TTL, before the absolute cap: inferred IDLE_TIMEOUT")
    void inferredIdleTimeoutWhenRecordGone() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("session:revoked:" + JTI)).thenReturn(null);
      when(valueOperations.get("session:" + JTI)).thenReturn(null);

      SessionCheckResult result = service.validateAndSlide(JTI, now.plusSeconds(3600));

      assertThat(result.isValid()).isFalse();
      assertThat(result.getRevokedReason()).isEqualTo(RevokedReason.IDLE_TIMEOUT);
    }
  }

  @Nested
  @DisplayName("validateAndSlide() — explicit revocation")
  class ExplicitRevocation {
    @Test
    @DisplayName("a tombstone short-circuits with its exact recorded reason")
    void tombstoneReasonWins() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("session:revoked:" + JTI)).thenReturn("LOGOUT");

      SessionCheckResult result = service.validateAndSlide(JTI, now.plusSeconds(3600));

      assertThat(result.isValid()).isFalse();
      assertThat(result.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
      // Never touches the live-record key once the tombstone answers the question.
      verify(valueOperations, never()).get("session:" + JTI);
    }
  }

  @Nested
  @DisplayName("status() — read-only, never slides (D3: GET /auth/session is non-renewing)")
  class StatusPeek {
    @Test
    @DisplayName("reads the current TTL without resetting it")
    void doesNotRewriteRecord() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("session:revoked:" + JTI)).thenReturn(null);
      when(redisTemplate.hasKey("session:" + JTI)).thenReturn(true);
      when(redisTemplate.getExpire("session:" + JTI, TimeUnit.SECONDS)).thenReturn(900L);

      SessionCheckResult result = service.status(JTI, now.plusSeconds(3600));

      assertThat(result.isValid()).isTrue();
      assertThat(result.getSecondsRemaining()).isEqualTo(900L);
      verify(valueOperations, never()).set(eq("session:" + JTI), anyString(), any());
    }
  }

  @Nested
  @DisplayName("revoke() / revokeAllForUser()")
  class Revocation {
    @Test
    @DisplayName("revoke() deletes the live record and writes a short-lived tombstone")
    void revokeDeletesAndTombstones() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);

      service.revoke(JTI, RevokedReason.LOGOUT);

      verify(redisTemplate).delete("session:" + JTI);
      verify(valueOperations).set("session:revoked:" + JTI, "LOGOUT", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("revokeAllForUser() revokes every indexed jti and clears the index")
    void revokeAllForUserRevokesEveryJti() {
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(redisTemplate.opsForSet()).thenReturn(setOperations);
      when(setOperations.members("session:user:" + USER_ID)).thenReturn(Set.of("jti-a", "jti-b"));

      service.revokeAllForUser(USER_ID, RevokedReason.PASSWORD_CHANGE);

      verify(redisTemplate).delete("session:jti-a");
      verify(redisTemplate).delete("session:jti-b");
      verify(valueOperations, times(2))
          .set(anyString(), eq("PASSWORD_CHANGE"), eq(Duration.ofMinutes(5)));
      verify(redisTemplate).delete("session:user:" + USER_ID);
    }
  }

  private String recordJson(Instant lastSeenAt, Instant absoluteExpiresAt) {
    try {
      return objectMapper.writeValueAsString(
          com.plantpal.session.entity.SessionRecord.builder()
              .jti(JTI)
              .userId(USER_ID)
              .issuedAt(lastSeenAt)
              .lastSeenAt(lastSeenAt)
              .absoluteExpiresAt(absoluteExpiresAt)
              .idleTtlSeconds(1800)
              .build());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
