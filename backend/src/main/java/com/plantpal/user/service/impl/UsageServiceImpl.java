package com.plantpal.user.service.impl;

import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.user.service.UsageService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageServiceImpl implements UsageService {
  private final JdbcTemplate jdbc;

  public UsageServiceImpl(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void requirePlantCapacity(Long userId) {
    Limits limits = lock(userId);
    // count(*) is never NULL, but queryForObject is nullable — don't auto-unbox it blindly.
    long count =
        Objects.requireNonNullElse(
            jdbc.queryForObject(
                "SELECT count(*) FROM plants WHERE user_id=? AND status='ACTIVE'",
                Long.class,
                userId),
            0L);
    if (count >= limits.plants())
      throw new PlantPalException(
          "Your plant limit has been reached. Archive a plant or ask an administrator to increase your allowance.",
          409);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void consumeAi(Long userId, boolean scan) {
    Limits limits = lock(userId);
    LocalDate day = LocalDate.now(ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO user_daily_usage(user_id, usage_date) VALUES (?,?) ON CONFLICT DO NOTHING",
        userId,
        day);
    int changed =
        jdbc.update(
            "UPDATE user_daily_usage SET scans=scans+?, ai_requests=ai_requests+1, updated_at=now() WHERE user_id=? AND usage_date=? AND ai_requests<? AND (?=0 OR scans<?)",
            scan ? 1 : 0,
            userId,
            day,
            limits.ai(),
            scan ? 1 : 0,
            limits.scans());
    if (changed == 0)
      throw new PlantPalException(
          "Your daily AI or scan allowance has been reached. It resets at midnight UTC; ask an administrator to increase it.",
          429);
  }

  @Override
  @Transactional(readOnly = true)
  public Usage usage(Long userId) {
    return jdbc.queryForObject(
        "SELECT (SELECT count(*) FROM plants WHERE user_id=u.id AND status='ACTIVE'), COALESCE(d.scans,0), COALESCE(d.ai_requests,0) FROM users u LEFT JOIN user_daily_usage d ON d.user_id=u.id AND d.usage_date=? WHERE u.id=?",
        (rs, n) -> new Usage(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
        LocalDate.now(ZoneOffset.UTC),
        userId);
  }

  private Limits lock(Long userId) {
    var rows =
        jdbc.query(
            "SELECT max_plants, daily_scan_limit, daily_ai_limit, status FROM users WHERE id=? FOR UPDATE",
            (rs, n) -> {
              if (!"ACTIVE".equals(rs.getString(4)))
                throw new PlantPalException("Account is not active", 403);
              return new Limits(rs.getInt(1), rs.getInt(2), rs.getInt(3));
            },
            userId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("User not found");
    return rows.get(0);
  }

  private record Limits(int plants, int scans, int ai) {}
}
