package com.plantpal.shared.seed;

import com.plantpal.user.entity.EmailVerificationToken;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.EmailVerificationTokenRepository;
import com.plantpal.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for email verification (PLA-26): an unverified account holding a valid
 * link, an unverified account whose link has expired, and a verified account. The links are fixed
 * strings so a reviewer can open {@code /login/verify-email?token=...} without a mailbox (dev sends
 * no real mail). Idempotent; never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class EmailVerificationReviewSeeder implements ApplicationRunner {

  static final String PENDING_EMAIL = "pla26-pending@plantpal.test";
  static final String EXPIRED_EMAIL = "pla26-expired@plantpal.test";
  static final String VERIFIED_EMAIL = "pla26-verified@plantpal.test";
  static final String PASSWORD = "pla26-password-1";
  static final String VALID_TOKEN = "pla26-valid-link-0001";
  static final String EXPIRED_TOKEN = "pla26-expired-link-0002";

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationReviewSeeder.class);

  private final UserRepository users;
  private final EmailVerificationTokenRepository tokens;
  private final PasswordEncoder passwordEncoder;

  public EmailVerificationReviewSeeder(
      UserRepository users,
      EmailVerificationTokenRepository tokens,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.tokens = tokens;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(PENDING_EMAIL).isPresent()) return;
    Instant now = Instant.now();

    User pending = createUser(PENDING_EMAIL, "Pending", null);
    link(pending, VALID_TOKEN, now.plus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

    User expired = createUser(EXPIRED_EMAIL, "Expired", null);
    link(expired, EXPIRED_TOKEN, now.minus(1, ChronoUnit.HOURS), now.minus(25, ChronoUnit.HOURS));

    createUser(VERIFIED_EMAIL, "Verified", now.minus(1, ChronoUnit.DAYS));
    log.info(
        "Seeded email verification review accounts {}, {} and {}",
        PENDING_EMAIL,
        EXPIRED_EMAIL,
        VERIFIED_EMAIL);
  }

  private User createUser(String email, String firstName, Instant verifiedAt) {
    return users.saveAndFlush(
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .firstName(firstName)
            .lastName("PLA-26")
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(verifiedAt)
            .build());
  }

  private void link(User user, String token, Instant expiresAt, Instant createdAt) {
    EmailVerificationToken row = new EmailVerificationToken();
    row.setUserId(user.getId());
    row.setTokenHash(sha256(token));
    row.setEmail(user.getEmail());
    row.setExpiresAt(expiresAt);
    row.setCreatedAt(createdAt);
    tokens.save(row);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
