package com.plantpal.shared.seed;

import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderPreferencesService;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserRole;
import com.plantpal.user.entity.UserStatus;
import com.plantpal.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalTime;
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
 * Dev/review-only test data for reminder emails (PLA-122). Three accounts, each with one plant and
 * a reminder that is already due and no push subscription: the default PUSH channel (receives the
 * fallback digest), an EMAIL account whose quiet hours cover almost the whole day (receives
 * nothing), and a French BOTH account (receives the digest in French). Idempotent; never active
 * outside the {@code dev} profile.
 */
@Component
@Profile("dev")
public class ReminderEmailReviewSeeder implements ApplicationRunner {

  static final String FALLBACK_EMAIL = "pla122-fallback@plantpal.test";
  static final String QUIET_EMAIL = "pla122-quiet@plantpal.test";
  static final String FRENCH_EMAIL = "pla122-fr@plantpal.test";
  static final String PASSWORD = "pla122-password-1";

  private static final Logger log = LoggerFactory.getLogger(ReminderEmailReviewSeeder.class);

  private final UserRepository users;
  private final PlantRepository plants;
  private final ReminderRepository reminders;
  private final ReminderPreferencesService preferences;
  private final PasswordEncoder passwordEncoder;

  public ReminderEmailReviewSeeder(
      UserRepository users,
      PlantRepository plants,
      ReminderRepository reminders,
      ReminderPreferencesService preferences,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.plants = plants;
    this.reminders = reminders;
    this.preferences = preferences;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.findByEmail(FALLBACK_EMAIL).isPresent()) {
      return;
    }
    User fallback = createUser(FALLBACK_EMAIL, "Fallback", "en");
    User quiet = createUser(QUIET_EMAIL, "Quiet", "en");
    User french = createUser(FRENCH_EMAIL, "Francais", "fr");

    seedDueReminder(fallback, "PLA-122 Monstera");
    seedDueReminder(quiet, "PLA-122 Pothos");
    seedDueReminder(french, "PLA-122 Ficus");

    ReminderPreferences quietPrefs = preferences.getOrCreate(quiet.getId());
    quietPrefs.setChannel(ReminderChannel.EMAIL);
    quietPrefs.setQuietHoursStart(LocalTime.of(0, 0));
    quietPrefs.setQuietHoursEnd(LocalTime.of(23, 59));

    ReminderPreferences frenchPrefs = preferences.getOrCreate(french.getId());
    frenchPrefs.setChannel(ReminderChannel.BOTH);
    frenchPrefs.setTimeZone("Europe/Paris");
    log.info(
        "Seeded reminder email review accounts {}, {} and {}",
        FALLBACK_EMAIL,
        QUIET_EMAIL,
        FRENCH_EMAIL);
  }

  private User createUser(String email, String firstName, String language) {
    return users.save(
        User.builder()
            .firstName(firstName)
            .lastName("PLA-122")
            .email(email)
            .passwordHash(passwordEncoder.encode(PASSWORD))
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .language(language)
            .build());
  }

  private void seedDueReminder(User owner, String nickname) {
    Plant plant =
        plants.save(
            Plant.builder()
                .userId(owner.getId())
                .nickname(nickname)
                .status(PlantStatus.ACTIVE)
                .build());
    reminders.save(
        Reminder.builder()
            .plantId(plant.getId())
            .userId(owner.getId())
            .careType(CareType.WATERING)
            .frequencyDays(7)
            .nextDueAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .build());
  }
}
