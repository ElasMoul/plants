package com.plantpal.shared.seed;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.plantgroup.entity.PlantGroup;
import com.plantpal.plantgroup.repository.PlantGroupRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
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
 * Dev/review-only test data for plant groups (PLA-110): an owner with two filled groups, an empty
 * group, an ungrouped plant, an archived plant still tied to a group and a recurring watering
 * reminder; plus a second user with a group of their own to try the ownership checks. Idempotent;
 * never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class PlantGroupReviewSeeder implements ApplicationRunner {

  static final String OWNER_EMAIL = "groups-owner@plantpal.test";
  static final String OTHER_EMAIL = "groups-other@plantpal.test";
  static final String PASSWORD = "groups-password-1";

  private static final Logger log = LoggerFactory.getLogger(PlantGroupReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final PlantGroupRepository groups;
  private final ReminderRepository reminders;
  private final PasswordEncoder passwordEncoder;

  public PlantGroupReviewSeeder(
      UserRepository users,
      PlantRepository plants,
      PlantGroupRepository groups,
      ReminderRepository reminders,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.plants = plants;
    this.groups = groups;
    this.reminders = reminders;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(OWNER_EMAIL).isPresent()) return;
    Long ownerId = createUser(OWNER_EMAIL, "Groups", "Owner").getId();
    Long otherId = createUser(OTHER_EMAIL, "Groups", "Other").getId();

    Long livingRoom = group(ownerId, "Living room");
    Long greenhouse = group(ownerId, "Greenhouse");
    group(ownerId, "Empty bed");
    group(otherId, "Someone else's bed");

    Plant monstera = plant(ownerId, "Monstera", livingRoom, PlantStatus.ACTIVE);
    plant(ownerId, "Fiddle-leaf fig", livingRoom, PlantStatus.ACTIVE);
    plant(ownerId, "Tomato", greenhouse, PlantStatus.ACTIVE);
    plant(ownerId, "Pothos (no group)", null, PlantStatus.ACTIVE);
    plant(ownerId, "Old fern (archived)", livingRoom, PlantStatus.ARCHIVED);
    plant(otherId, "Other user's cactus", null, PlantStatus.ACTIVE);

    reminders.save(
        Reminder.builder()
            .plantId(monstera.getId())
            .userId(ownerId)
            .careType(CareType.WATERING)
            .frequencyDays(7)
            .nextDueAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .build());
    log.info("Seeded plant group review accounts {} and {}", OWNER_EMAIL, OTHER_EMAIL);
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

  private Long group(Long userId, String name) {
    return groups.save(PlantGroup.builder().userId(userId).name(name).build()).getId();
  }

  private Plant plant(Long userId, String nickname, Long groupId, PlantStatus status) {
    return plants.save(
        Plant.builder().userId(userId).nickname(nickname).status(status).groupId(groupId).build());
  }
}
