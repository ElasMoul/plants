package com.plantpal.shared.seed;

import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for account management (PLA-24): a verified member to edit the profile,
 * change the password and start an email change with, plus a second account whose address is
 * already taken (the duplicate-address edge case). Idempotent; never active outside the {@code dev}
 * profile.
 */
@Component
@Profile("dev")
public class AccountManagementReviewSeeder implements ApplicationRunner {

  static final String MEMBER_EMAIL = "pla24-member@plantpal.test";
  static final String TAKEN_EMAIL = "pla24-taken@plantpal.test";
  static final String PASSWORD = "pla24-password-1";

  private static final Logger log = LoggerFactory.getLogger(AccountManagementReviewSeeder.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public AccountManagementReviewSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(MEMBER_EMAIL).isPresent()) return;
    Instant verifiedAt = Instant.now().minus(1, ChronoUnit.DAYS);
    create(MEMBER_EMAIL, "Member", verifiedAt);
    create(TAKEN_EMAIL, "Taken", verifiedAt);
    log.info("Seeded account management review accounts {} and {}", MEMBER_EMAIL, TAKEN_EMAIL);
  }

  private void create(String email, String firstName, Instant verifiedAt) {
    users.saveAndFlush(
        User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .firstName(firstName)
            .lastName("PLA-24")
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(verifiedAt)
            .build());
  }
}
