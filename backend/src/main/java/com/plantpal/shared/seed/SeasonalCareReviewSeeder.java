package com.plantpal.shared.seed;

import com.plantpal.plant.entity.Placement;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
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
 * Dev/review-only test data for seasonal care intervals (PLA-108): a northern user (FR) with an
 * indoor and an outdoor plant of the same species, each with a 7-day watering and a 14-day
 * fertilizing reminder; plus a southern user (AU) with the same indoor setup to see the seasons
 * swap. Idempotent; never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class SeasonalCareReviewSeeder implements ApplicationRunner {

  static final String NORTH_EMAIL = "pla108-north@plantpal.test";
  static final String SOUTH_EMAIL = "pla108-south@plantpal.test";
  static final String PASSWORD = "pla108-password-1";

  private static final Logger log = LoggerFactory.getLogger(SeasonalCareReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final ReminderRepository reminders;
  private final PasswordEncoder passwordEncoder;

  public SeasonalCareReviewSeeder(
      UserRepository users,
      PlantRepository plants,
      ReminderRepository reminders,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.plants = plants;
    this.reminders = reminders;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(NORTH_EMAIL).isPresent()) return;
    Long northId = createUser(NORTH_EMAIL, "North", "FR").getId();
    Long southId = createUser(SOUTH_EMAIL, "South", "AU").getId();

    careFor(northId, plant(northId, "PLA-108 Monstera (indoor)", Placement.INDOOR));
    careFor(northId, plant(northId, "PLA-108 Monstera (outdoor)", Placement.OUTDOOR));
    careFor(southId, plant(southId, "PLA-108 Monstera (indoor, south)", Placement.INDOOR));
    log.info("Seeded seasonal care review accounts {} and {}", NORTH_EMAIL, SOUTH_EMAIL);
  }

  private User createUser(String email, String firstName, String country) {
    return users.save(
        User.builder()
            .firstName(firstName)
            .lastName("PLA-108")
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .regionCountry(country)
            .build());
  }

  private Plant plant(Long userId, String nickname, Placement placement) {
    return plants.save(
        Plant.builder()
            .userId(userId)
            .nickname(nickname)
            .species("Monstera deliciosa")
            .status(PlantStatus.ACTIVE)
            .placement(placement)
            .build());
  }

  private void careFor(Long userId, Plant plant) {
    reminder(userId, plant, CareType.WATERING, 7);
    reminder(userId, plant, CareType.FERTILIZING, 14);
  }

  private void reminder(Long userId, Plant plant, CareType careType, int days) {
    reminders.save(
        Reminder.builder()
            .plantId(plant.getId())
            .userId(userId)
            .careType(careType)
            .frequencyDays(days)
            .nextDueAt(Instant.now().plus(1, ChronoUnit.DAYS))
            .build());
  }
}
