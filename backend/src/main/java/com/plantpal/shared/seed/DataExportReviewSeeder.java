package com.plantpal.shared.seed;

import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStageStatus;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for the self-service data export (PLA-112): an owner with an active
 * plant, an archived plant, care logs, a scan and a photo (including a value starting with "=" to
 * show CSV neutralising), plus a second user whose data must never appear in the owner's export.
 * Idempotent; never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class DataExportReviewSeeder implements ApplicationRunner {

  static final String OWNER_EMAIL = "export-owner@plantpal.test";
  static final String OTHER_EMAIL = "export-other@plantpal.test";
  static final String PASSWORD = "export-password-1";

  // 1x1 transparent PNG.
  private static final byte[] PNG =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

  private static final Logger log = LoggerFactory.getLogger(DataExportReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final CareLogRepository careLogs;
  private final IdentificationRepository identifications;
  private final PasswordEncoder passwordEncoder;
  private final String photoDir;

  public DataExportReviewSeeder(
      UserRepository users,
      PlantRepository plants,
      CareLogRepository careLogs,
      IdentificationRepository identifications,
      PasswordEncoder passwordEncoder,
      @Value("${app.storage.local-path:/tmp/plantpal/photos}") String photoDir) {
    this.users = users;
    this.plants = plants;
    this.careLogs = careLogs;
    this.identifications = identifications;
    this.passwordEncoder = passwordEncoder;
    this.photoDir = photoDir;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(OWNER_EMAIL).isPresent()) return;
    seed(createUser(OWNER_EMAIL, "Export", "Owner").getId(), "Owner");
    seed(createUser(OTHER_EMAIL, "Export", "Other").getId(), "Other");
    log.info("Seeded data export review accounts {} and {}", OWNER_EMAIL, OTHER_EMAIL);
  }

  private void seed(Long userId, String who) {
    String photo = writePhoto();
    Plant fern =
        plants.save(
            Plant.builder()
                .userId(userId)
                .nickname(who + " fern")
                .notes("=HYPERLINK(\"http://example.com\") notes, with a comma")
                .status(PlantStatus.ACTIVE)
                .photoUrl(photo)
                .build());
    plants.save(
        Plant.builder()
            .userId(userId)
            .nickname(who + " old cactus (archived)")
            .status(PlantStatus.ARCHIVED)
            .build());
    careLogs.save(
        CareLog.builder()
            .plantId(fern.getId())
            .userId(userId)
            .careType(CareType.WATERING)
            .notes(who + " watered the fern")
            .build());
    identifications.save(
        Identification.builder()
            .userId(userId)
            .plantId(fern.getId())
            .dateTaken(Instant.now())
            .photoUrl(photo)
            .scientificName("Nephrolepis exaltata")
            .commonName(who + " Boston fern")
            .userContext(who + " scan note")
            .status(IdentificationStatus.COMPLETED)
            .identificationStatus(IdentificationStageStatus.COMPLETED)
            .annotationStatus(IdentificationStageStatus.SKIPPED)
            .candidateStatus(IdentificationStageStatus.SKIPPED)
            .build());
  }

  private String writePhoto() {
    String filename = UUID.randomUUID() + ".png";
    try {
      Path dir = Path.of(photoDir);
      Files.createDirectories(dir);
      Files.write(dir.resolve(filename), PNG);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return "/photos/" + filename;
  }

  private User createUser(String email, String firstName, String lastName) {
    return users.save(
        User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build());
  }
}
