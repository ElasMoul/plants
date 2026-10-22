package com.plantpal.shared.seed;

import com.plantpal.journal.entity.JournalEventType;
import com.plantpal.journal.entity.PlantJournalEvent;
import com.plantpal.journal.repository.PlantJournalEventRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/review-only test data for plant journal events (PLA-111): an owner with a plant carrying one
 * event of each type (one without a note), plus a second user with a plant of their own to try the
 * ownership checks. Idempotent; never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class JournalReviewSeeder implements ApplicationRunner {

  static final String OWNER_EMAIL = "journal-owner@plantpal.test";
  static final String OTHER_EMAIL = "journal-other@plantpal.test";
  static final String PASSWORD = "journal-password-1";

  private static final Logger log = LoggerFactory.getLogger(JournalReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final PlantJournalEventRepository events;
  private final PasswordEncoder passwordEncoder;

  public JournalReviewSeeder(
      UserRepository users,
      PlantRepository plants,
      PlantJournalEventRepository events,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.plants = plants;
    this.events = events;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(OWNER_EMAIL).isPresent()) return;
    Long ownerId = createUser(OWNER_EMAIL, "Owner").getId();
    Long otherId = createUser(OTHER_EMAIL, "Other").getId();

    Long monstera = plant(ownerId, "Monstera");
    plant(otherId, "Other user's cactus");

    LocalDate today = LocalDate.now();
    event(
        ownerId,
        monstera,
        JournalEventType.REPOTTING,
        today.minusDays(120),
        "Moved to a 25 cm pot");
    event(ownerId, monstera, JournalEventType.PRUNING, today.minusDays(90), null);
    event(ownerId, monstera, JournalEventType.FLOWERING, today.minusDays(60), "First spathe");
    event(ownerId, monstera, JournalEventType.FRUITING, today.minusDays(30), "Two small fruits");
    event(
        ownerId,
        monstera,
        JournalEventType.PROPAGATION,
        today.minusDays(7),
        "Stem cutting in water");
    log.info("Seeded plant journal review accounts {} and {}", OWNER_EMAIL, OTHER_EMAIL);
  }

  private User createUser(String email, String lastName) {
    return users.save(
        User.builder()
            .firstName("Journal")
            .lastName(lastName)
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build());
  }

  private Long plant(Long userId, String nickname) {
    return plants
        .save(Plant.builder().userId(userId).nickname(nickname).status(PlantStatus.ACTIVE).build())
        .getId();
  }

  private void event(
      Long userId, Long plantId, JournalEventType type, LocalDate date, String note) {
    events.save(
        PlantJournalEvent.builder()
            .plantId(plantId)
            .userId(userId)
            .type(type)
            .eventDate(date)
            .note(note)
            .build());
  }
}
