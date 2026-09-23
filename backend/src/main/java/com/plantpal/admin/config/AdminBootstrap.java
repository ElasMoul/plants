package com.plantpal.admin.config;

import com.plantpal.admin.repository.AdminRepository;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, opt-in promotion of an existing account; never grants roles during registration. */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
  private final UserRepository users;
  private final AdminRepository audit;

  @Value("${app.admin.bootstrap-email:}")
  private String email;

  public AdminBootstrap(UserRepository users, AdminRepository audit) {
    this.users = users;
    this.audit = audit;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank()) return;
    audit.lockAccessChanges();
    var user =
        users
            .findByEmail(email.trim())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Admin bootstrap requires an existing account. Register first, then configure APP_ADMIN_BOOTSTRAP_EMAIL."));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new IllegalStateException("Admin bootstrap requires an active account.");
    }
    if (user.getRole() == UserRole.ADMIN) return;
    user.setRole(UserRole.ADMIN);
    users.save(user);
    audit.record(
        user.getId(),
        "Initial administrator provisioned by server configuration",
        "user:" + user.getId());
    log.info(
        "Administrator bootstrapped for userId={}; remove bootstrap email after setup",
        user.getId());
  }
}
