package com.plantpal.admin.repository;

import com.plantpal.admin.dto.AdminDtos.Activity;
import com.plantpal.admin.dto.AdminDtos.Day;
import com.plantpal.admin.dto.AdminDtos.Overview;
import com.plantpal.admin.dto.AdminDtos.Scans;
import com.plantpal.admin.dto.AdminDtos.UsageDay;
import com.plantpal.admin.dto.AdminDtos.VerdictCounts;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
  private final JdbcTemplate jdbc;

  public AdminRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Overview overview() {
    Map<String, Long> totals = new LinkedHashMap<>();
    totals.put("users", count("SELECT count(*) FROM users"));
    totals.put("activeUsers", count("SELECT count(*) FROM users WHERE status = 'ACTIVE'"));
    totals.put(
        "newUsers",
        count("SELECT count(*) FROM users WHERE created_at >= now() - interval '30 days'"));
    totals.put("plants", count("SELECT count(*) FROM plants WHERE status = 'ACTIVE'"));
    totals.put("species", count("SELECT count(*) FROM species"));
    totals.put("treatments", count("SELECT count(*) FROM treatments WHERE status = 'IN_PROGRESS'"));
    totals.put(
        "overdue", count("SELECT count(*) FROM reminders WHERE enabled AND next_due_at < now()"));
    totals.put(
        "scans",
        count(
            "SELECT count(*) FROM identifications WHERE created_at >= now() - interval '30 days'"));
    totals.put(
        "failedScans",
        count(
            "SELECT count(*) FROM identifications WHERE status = 'FAILED' AND created_at >= now() - interval '30 days'"));
    totals.put(
        "pendingScans",
        count("SELECT count(*) FROM identifications WHERE status IN ('PENDING', 'PROCESSING')"));
    return new Overview(totals, scanDays(), Instant.now());
  }

  /** Verdicts whose verdict_at is in [from, to); a scan counts once, under its latest verdict. */
  public VerdictCounts verdictCounts(Instant from, Instant to) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FILTER (WHERE verdict = 'RIGHT') AS right_count,
          count(*) FILTER (WHERE verdict = 'WRONG') AS wrong_count,
          count(*) FILTER (WHERE verdict = 'UNSURE') AS unsure_count
        FROM identifications
        WHERE verdict IS NOT NULL AND verdict_at >= ? AND verdict_at < ?
        """,
        (rs, row) ->
            new VerdictCounts(
                rs.getLong("right_count"), rs.getLong("wrong_count"), rs.getLong("unsure_count")),
        java.sql.Timestamp.from(from),
        java.sql.Timestamp.from(to));
  }

  /** Scan counts from identifications whose created_at is in [from, to). */
  public Scans scanCounts(Instant from, Instant to) {
    return jdbc.queryForObject(
        """
        SELECT count(*) AS total,
          count(*) FILTER (WHERE status = 'COMPLETED') AS completed,
          count(*) FILTER (WHERE status = 'FAILED') AS failed,
          count(*) FILTER (WHERE status IN ('PENDING', 'PROCESSING')) AS pending
        FROM identifications WHERE created_at >= ? AND created_at < ?
        """,
        (rs, row) ->
            new Scans(
                rs.getLong("total"),
                rs.getLong("completed"),
                rs.getLong("failed"),
                rs.getLong("pending")),
        java.sql.Timestamp.from(from),
        java.sql.Timestamp.from(to));
  }

  /** Per-day AI usage from user_daily_usage for usage_date in [from, to] (inclusive). */
  public List<UsageDay> usageDays(LocalDate from, LocalDate to) {
    return jdbc.query(
        """
        SELECT to_char(usage_date, 'YYYY-MM-DD') AS day,
          COALESCE(SUM(ai_requests), 0) AS ai_requests, COALESCE(SUM(scans), 0) AS scans
        FROM user_daily_usage WHERE usage_date >= ? AND usage_date <= ?
        GROUP BY usage_date ORDER BY usage_date
        """,
        (rs, row) ->
            new UsageDay(rs.getString("day"), rs.getLong("ai_requests"), rs.getLong("scans")),
        java.sql.Date.valueOf(from),
        java.sql.Date.valueOf(to));
  }

  public Page<Activity> activity(Pageable page) {
    var rows =
        jdbc.query(
            "SELECT * FROM admin_activity ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            (rs, row) ->
                new Activity(
                    rs.getLong("id"),
                    rs.getLong("actor_id"),
                    rs.getString("action"),
                    rs.getString("target"),
                    rs.getTimestamp("created_at").toInstant()),
            page.getPageSize(),
            page.getOffset());
    return new PageImpl<>(rows, page, count("SELECT count(*) FROM admin_activity"));
  }

  public void record(Long actorId, String action, String target) {
    jdbc.update(
        "INSERT INTO admin_activity (actor_id, action, target, created_by, updated_by) VALUES (?, ?, ?, ?, ?)",
        actorId,
        action,
        target,
        actorId.toString(),
        actorId.toString());
  }

  // Serializes permission changes across instances; re-check the actor after acquiring it.
  public void lockAccessChanges() {
    jdbc.execute("SELECT pg_advisory_xact_lock(734034)");
  }

  private List<Day> scanDays() {
    return jdbc.query(
        """
        SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
          count(i.id) FILTER (WHERE i.status = 'COMPLETED') AS completed,
          count(i.id) FILTER (WHERE i.status = 'FAILED') AS failed,
          count(i.id) FILTER (WHERE i.status IN ('PENDING', 'PROCESSING')) AS pending
        FROM generate_series((now() AT TIME ZONE 'UTC')::date - 13,
          (now() AT TIME ZONE 'UTC')::date, interval '1 day') d(day)
        LEFT JOIN identifications i ON i.created_at >= d.day AT TIME ZONE 'UTC'
          AND i.created_at < (d.day + interval '1 day') AT TIME ZONE 'UTC'
        GROUP BY d.day ORDER BY d.day
        """,
        (rs, row) ->
            new Day(
                rs.getString("day"),
                rs.getLong("completed"),
                rs.getLong("failed"),
                rs.getLong("pending")));
  }

  /** Null-safe: a NULL aggregate (e.g. a future SUM over no rows) reads as 0, never an NPE. */
  private long count(String sql) {
    return Objects.requireNonNullElse(jdbc.queryForObject(sql, Long.class), 0L);
  }
}
