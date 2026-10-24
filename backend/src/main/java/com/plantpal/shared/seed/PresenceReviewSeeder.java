package com.plantpal.shared.seed;

import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Dev/review-only test data for live-user presence (PLA-33): an administrator who can open the
 * admin overview, a member to sign in with as a second session, and a member whose last activity is
 * long past the online window (so they are never counted). Idempotent; {@code dev} profile only.
 */
@Component
@Profile("dev")
public class PresenceReviewSeeder implements ApplicationRunner {

  static final String ADMIN_EMAIL = "pla33-presence-admin@plantpal.test";
  static final String MEMBER_EMAIL = "pla33-presence-member@plantpal.test";
  static final String STALE_EMAIL = "pla33-presence-stale@plantpal.test";
  static final String PASSWORD = "pla33-presence-password";

  private static final Logger log = LoggerFactory.getLogger(PresenceReviewSeeder.class);

  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public PresenceReviewSeeder(
      UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(ADMIN_EMAIL).isPresent()) return;
    createUser(ADMIN_EMAIL, "Admin", UserRole.ADMIN);
    createUser(MEMBER_EMAIL, "Member", UserRole.USER);
    Long stale = createUser(STALE_EMAIL, "Stale", UserRole.USER).getId();
    jdbc.update(
        "UPDATE users SET last_active_at=? WHERE id=?",
        Timestamp.from(Instant.now().minusSeconds(3600)),
        stale);
    log.info("Seeded presence review accounts {}, {}, {}", ADMIN_EMAIL, MEMBER_EMAIL, STALE_EMAIL);
  }

  private User createUser(String email, String lastName, UserRole role) {
    return users.save(
        User.builder()
            .firstName("Presence")
            .lastName(lastName)
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }
}
