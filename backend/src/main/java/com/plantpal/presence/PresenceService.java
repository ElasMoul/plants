package com.plantpal.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {
  static final Duration THROTTLE = Duration.ofSeconds(30);

  public record Snapshot(long onlineCount, long windowSeconds, Instant asOf) {}

  private final PresenceRepository repository;
  private final Clock clock;
  private final Duration onlineWindow;

  public PresenceService(
      PresenceRepository repository,
      Clock clock,
      @Value("${app.presence.online-window:PT5M}") Duration onlineWindow) {
    this.repository = repository;
    this.clock = clock;
    this.onlineWindow = onlineWindow;
  }

  public void heartbeat(Long userId) {
    repository.touch(userId, clock.instant(), THROTTLE);
  }

  public Snapshot snapshot() {
    Instant now = clock.instant();
    return new Snapshot(
        repository.countActiveSince(now.minus(onlineWindow)), onlineWindow.toSeconds(), now);
  }
}
