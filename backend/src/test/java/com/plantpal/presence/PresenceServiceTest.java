package com.plantpal.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
  @Mock private PresenceRepository repository;

  private PresenceService service(Duration window) {
    return new PresenceService(repository, Clock.fixed(NOW, ZoneOffset.UTC), window);
  }

  @Test
  void heartbeatTouchesWithClockAndThirtySecondThrottle() {
    service(Duration.ofMinutes(5)).heartbeat(7L);
    verify(repository).touch(7L, NOW, Duration.ofSeconds(30));
  }

  @Test
  void snapshotCountsFromWindowStart() {
    when(repository.countActiveSince(NOW.minusSeconds(300))).thenReturn(3L);
    var snap = service(Duration.ofMinutes(5)).snapshot();
    assertThat(snap.onlineCount()).isEqualTo(3);
    assertThat(snap.windowSeconds()).isEqualTo(300);
    assertThat(snap.asOf()).isEqualTo(NOW);
  }

  @Test
  void windowIsConfigurable() {
    when(repository.countActiveSince(any())).thenReturn(0L);
    assertThat(service(Duration.ofSeconds(90)).snapshot().windowSeconds()).isEqualTo(90);
  }
}
