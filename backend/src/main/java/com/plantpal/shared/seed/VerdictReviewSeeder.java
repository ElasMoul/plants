package com.plantpal.shared.seed;

import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStageStatus;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.entity.IdentificationVerdict;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
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
 * Dev/review-only test data for the identification verdict feature (PLA-102): a regular account
 * with completed scans (one unrated, with PlantNet candidates; three already rated) and an
 * administrator account to see the counts. Idempotent; never active outside the {@code dev}
 * profile.
 */
@Component
@Profile("dev")
public class VerdictReviewSeeder implements ApplicationRunner {

  static final String OWNER_EMAIL = "verdict-owner@plantpal.test";
  static final String ADMIN_EMAIL = "verdict-admin@plantpal.test";
  static final String PASSWORD = "verdict-password-1";

  private static final Logger log = LoggerFactory.getLogger(VerdictReviewSeeder.class);
  private static final String CANDIDATES =
      "[{\"score\":0.61,\"scientificName\":\"Ficus lyrata\",\"commonNames\":[\"Fiddle-leaf fig\"]},"
          + "{\"score\":0.22,\"scientificName\":\"Ficus elastica\",\"commonNames\":[\"Rubber plant\"]}]";

  private final UserRepository users;
  private final IdentificationRepository identifications;
  private final PasswordEncoder passwordEncoder;

  public VerdictReviewSeeder(
      UserRepository users,
      IdentificationRepository identifications,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.identifications = identifications;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(ADMIN_EMAIL).isEmpty()) {
      createUser(ADMIN_EMAIL, UserRole.ADMIN);
    }
    if (users.findByEmail(OWNER_EMAIL).isPresent()) return;
    Long ownerId = createUser(OWNER_EMAIL, UserRole.USER).getId();
    seedScan(ownerId, "Monstera deliciosa", "Swiss cheese plant", CANDIDATES, null, null);
    seedScan(ownerId, "Ficus lyrata", "Fiddle-leaf fig", null, IdentificationVerdict.RIGHT, null);
    seedScan(
        ownerId, "Pothos", "Devil's ivy", null, IdentificationVerdict.WRONG, "Epipremnum aureum");
    seedScan(ownerId, "Calathea", "Prayer plant", null, IdentificationVerdict.UNSURE, null);
    log.info("Seeded verdict review accounts {} and {}", OWNER_EMAIL, ADMIN_EMAIL);
  }

  private User createUser(String email, UserRole role) {
    return users.save(
        User.builder()
            .firstName(role == UserRole.ADMIN ? "Verdict" : "Review")
            .lastName(role == UserRole.ADMIN ? "Admin" : "Owner")
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(role)
            .build());
  }

  private void seedScan(
      Long userId,
      String scientific,
      String common,
      String candidates,
      IdentificationVerdict verdict,
      String species) {
    identifications.save(
        Identification.builder()
            .userId(userId)
            .photoUrl("/photos/seed.jpg")
            .status(IdentificationStatus.COMPLETED)
            .identificationStatus(IdentificationStageStatus.COMPLETED)
            .annotationStatus(IdentificationStageStatus.SKIPPED)
            .candidateStatus(IdentificationStageStatus.SKIPPED)
            .scientificName(scientific)
            .commonName(common)
            .confidence(0.8)
            .plantnetCandidates(candidates)
            .verdict(verdict)
            .verdictSpecies(species)
            .verdictAt(verdict == null ? null : Instant.now().minus(1, ChronoUnit.HOURS))
            .build());
  }
}
