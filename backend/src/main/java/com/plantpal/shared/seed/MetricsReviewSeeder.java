package com.plantpal.shared.seed;

import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for the admin operational metrics (PLA-34): an administrator plus a
 * member with scans in every status (2 completed, 1 failed, 1 pending) and two days of AI usage, so
 * the metrics panel shows non-zero scans, errors, AI usage and (with a rate set) cost. Idempotent;
 * never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class MetricsReviewSeeder implements ApplicationRunner {

  static final String ADMIN_EMAIL = "pla34-metrics-admin@plantpal.test";
  static final String MEMBER_EMAIL = "pla34-metrics-member@plantpal.test";
  static final String PASSWORD = "pla34-metrics-password-1";

  private static final Logger log = LoggerFactory.getLogger(MetricsReviewSeeder.class);

  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public MetricsReviewSeeder(
      UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(ADMIN_EMAIL).isEmpty()) createUser(ADMIN_EMAIL, UserRole.ADMIN);
    if (users.findByEmail(MEMBER_EMAIL).isPresent()) return;
    Long memberId = createUser(MEMBER_EMAIL, UserRole.USER).getId();
    for (String status : new String[] {"COMPLETED", "COMPLETED", "FAILED", "PENDING"}) {
      jdbc.update(
          "INSERT INTO identifications(user_id,status,common_name) VALUES (?,?,?)",
          memberId,
          status,
          "PLA-34 metrics scan");
    }
    jdbc.update(
        "INSERT INTO user_daily_usage(user_id,usage_date,scans,ai_requests) VALUES (?,?,?,?)",
        memberId,
        java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneOffset.UTC)),
        3,
        6);
    jdbc.update(
        "INSERT INTO user_daily_usage(user_id,usage_date,scans,ai_requests) VALUES (?,?,?,?)",
        memberId,
        java.sql.Date.valueOf(java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1)),
        1,
        4);
    log.info("Seeded metrics review accounts {} and {}", ADMIN_EMAIL, MEMBER_EMAIL);
  }

  private User createUser(String email, UserRole role) {
    return users.save(
        User.builder()
            .firstName("PLA-34")
            .lastName(role == UserRole.ADMIN ? "Metrics Admin" : "Metrics Member")
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }
}
