package com.plantpal.shared.seed;

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
 * Dev/review-only test data for units and time zones (PLA-132): a US-region user with no unit
 * choice (defaults to imperial) and a New York zone, and a French-region user with no choice
 * (defaults to metric) and a Berlin zone, each with a plant and a daily watering reminder.
 * Idempotent; never active outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class UnitsReviewSeeder implements ApplicationRunner {

  static final String US_EMAIL = "units-us-pla132@plantpal.test";
  static final String EU_EMAIL = "units-eu-pla132@plantpal.test";
  static final String PASSWORD = "units-password-1";

  private static final Logger log = LoggerFactory.getLogger(UnitsReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final ReminderRepository reminders;
  private final PasswordEncoder passwordEncoder;

  public UnitsReviewSeeder(
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
    if (users.findByEmail(US_EMAIL).isPresent()) return;
    seed(US_EMAIL, "US", "America/New_York", "Units PLA-132 US plant");
    seed(EU_EMAIL, "FR", "Europe/Berlin", "Units PLA-132 FR plant");
    log.info("Seeded units review accounts {} and {}", US_EMAIL, EU_EMAIL);
  }

  private void seed(String email, String country, String timeZone, String plantName) {
    User user =
        users.save(
            User.builder()
                .firstName("Units")
                .lastName("PLA-132")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .regionCountry(country)
                .timeZone(timeZone)
                .build());
    Plant plant =
        plants.save(
            Plant.builder()
                .userId(user.getId())
                .nickname(plantName)
                .status(PlantStatus.ACTIVE)
                .build());
    reminders.save(
        Reminder.builder()
            .plantId(plant.getId())
            .userId(user.getId())
            .careType(CareType.WATERING)
            .frequencyDays(1)
            .nextDueAt(Instant.now().minus(1, ChronoUnit.HOURS))
            .build());
  }
}
