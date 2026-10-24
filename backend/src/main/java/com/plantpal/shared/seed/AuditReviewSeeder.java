package com.plantpal.shared.seed;

import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for the append-only admin audit history (PLA-36): an administrator, a
 * member the administrator can edit, and one pre-existing activity row. Idempotent; {@code dev}
 * profile only.
 */
@Component
@Profile("dev")
public class AuditReviewSeeder implements ApplicationRunner {

  static final String ADMIN_EMAIL = "pla36-audit-admin@plantpal.test";
  static final String MEMBER_EMAIL = "pla36-audit-member@plantpal.test";
  static final String PASSWORD = "pla36-audit-password";

  private final UserRepository users;
  private final AdminRepository audit;
  private final PasswordEncoder passwordEncoder;

  public AuditReviewSeeder(
      UserRepository users, AdminRepository audit, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.audit = audit;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(ADMIN_EMAIL).isPresent()) return;
    User admin = createUser(ADMIN_EMAIL, "Admin", UserRole.ADMIN);
    User member = createUser(MEMBER_EMAIL, "Member", UserRole.USER);
    audit.record(admin.getId(), "PLA-36 seeded sample entry", "user:" + member.getId());
  }

  private User createUser(String email, String lastName, UserRole role) {
    return users.save(
        User.builder()
            .firstName("Audit")
            .lastName(lastName)
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }
}
