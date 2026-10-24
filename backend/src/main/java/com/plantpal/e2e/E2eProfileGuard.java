package com.plantpal.e2e;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to boot when the {@code e2e} profile (stubbed AI providers) is combined with a deployed
 * profile. The profile exists only for the end-to-end smoke test and must never serve real users.
 */
@Configuration
@Profile("e2e")
public class E2eProfileGuard {

  private final Environment environment;

  public E2eProfileGuard(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  void rejectDeployedProfiles() {
    if (environment.acceptsProfiles(Profiles.of("prod", "staging", "platform"))) {
      throw new IllegalStateException(
          "The e2e profile stubs every AI provider and must not run with prod, staging or platform");
    }
  }
}
