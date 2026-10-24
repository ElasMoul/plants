package com.plantpal.presence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PresenceRepository {
  private static final String TOUCH =
      """
      UPDATE users SET last_active_at=:now
      WHERE id=:id AND (last_active_at IS NULL OR last_active_at < :cutoff)
      """;
  private static final String COUNT =
      "SELECT count(*) FROM users WHERE status='ACTIVE' AND last_active_at > :since";
  private final NamedParameterJdbcTemplate jdbc;

  public PresenceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Writes only when the stored value is older than the throttle; returns rows written. */
  public int touch(Long userId, Instant now, Duration throttle) {
    return jdbc.update(
        TOUCH,
        new MapSqlParameterSource()
            .addValue("id", userId)
            .addValue("now", Timestamp.from(now))
            .addValue("cutoff", Timestamp.from(now.minus(throttle))));
  }

  /** Active users whose last activity is strictly newer than {@code since}. */
  public long countActiveSince(Instant since) {
    Long total = jdbc.queryForObject(COUNT, Map.of("since", Timestamp.from(since)), Long.class);
    return total == null ? 0 : total;
  }
}
