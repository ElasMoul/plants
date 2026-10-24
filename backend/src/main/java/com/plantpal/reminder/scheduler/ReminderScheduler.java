package com.plantpal.reminder.scheduler;

import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.PushSubscriptionRepository;
import com.plantpal.reminder.repository.ReminderPreferencesRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.ReminderDeliveryPolicy;
import com.plantpal.reminder.service.ReminderDeliveryPolicy.Decision;
import com.plantpal.reminder.service.ReminderDigestMailer;
import com.plantpal.reminder.service.ReminderPreferencesService;
import com.plantpal.reminder.service.WebPushService;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs hourly. Each user with due reminders is notified at most once per local day, from 08:00 in
 * their own time zone and never inside their quiet hours; {@link ReminderDeliveryPolicy} decides
 * whether that is push, email or both.
 */
@Component
public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final ReminderRepository reminderRepository;
  private final WebPushService webPushService;
  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final ReminderPreferencesRepository preferencesRepository;
  private final ReminderPreferencesService preferencesService;
  private final UserRepository userRepository;
  private final ReminderDigestMailer digestMailer;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ReminderScheduler(
      ReminderRepository reminderRepository,
      WebPushService webPushService,
      PushSubscriptionRepository pushSubscriptionRepository,
      ReminderPreferencesRepository preferencesRepository,
      ReminderPreferencesService preferencesService,
      UserRepository userRepository,
      ReminderDigestMailer digestMailer,
      TransactionTemplate tx,
      Clock clock) {
    this.reminderRepository = reminderRepository;
    this.webPushService = webPushService;
    this.pushSubscriptionRepository = pushSubscriptionRepository;
    this.preferencesRepository = preferencesRepository;
    this.preferencesService = preferencesService;
    this.userRepository = userRepository;
    this.digestMailer = digestMailer;
    this.tx = tx;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 * * * *")
  public void sendDueReminderNotifications() {
    Instant now = Instant.now(clock);
    List<Reminder> dueReminders = reminderRepository.findAllDue(now);
    Map<Long, List<Reminder>> dueByUserId =
        dueReminders.stream().collect(Collectors.groupingBy(Reminder::getUserId));
    Map<Long, ReminderPreferences> prefsByUserId =
        preferencesRepository.findAllById(dueByUserId.keySet()).stream()
            .collect(Collectors.toMap(ReminderPreferences::getUserId, Function.identity()));
    Map<Long, User> usersById =
        userRepository.findAllById(dueByUserId.keySet()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    int notified = 0;
    int skipped = 0;
    int failed = 0;
    for (Map.Entry<Long, List<Reminder>> entry : dueByUserId.entrySet()) {
      Long userId = entry.getKey();
      try {
        ReminderPreferences prefs =
            prefsByUserId.getOrDefault(userId, ReminderPreferences.defaults(userId));
        if (notifyUser(usersById.get(userId), prefs, entry.getValue(), now)) {
          notified++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        failed++;
        log.warn(
            "Failed to send reminder notification: userId={}, error={}", userId, e.getMessage());
      }
    }

    log.info(
        "Reminder notifications processed: notified={}, skipped={}, failed={},"
            + " usersWithDueReminders={}, totalDueReminders={}",
        notified,
        skipped,
        failed,
        dueByUserId.size(),
        dueReminders.size());
  }

  private boolean notifyUser(
      User user, ReminderPreferences prefs, List<Reminder> due, Instant now) {
    if (user == null) {
      return false;
    }
    ZonedDateTime localNow = now.atZone(zoneOf(prefs));
    boolean hasPush = pushSubscriptionRepository.existsByUserIdAndEnabledTrue(user.getId());
    Decision decision = ReminderDeliveryPolicy.decide(prefs, hasPush, localNow);
    if (!decision.any()) {
      return false;
    }
    // Queue the email and stamp the day together, so a rollback never leaves one without the other.
    tx.executeWithoutResult(
        status -> {
          ReminderPreferences stored = preferencesService.getOrCreate(user.getId());
          if (decision.email()) {
            digestMailer.enqueue(user, due, stored.getUnsubscribeToken());
          }
          stored.setLastNotifiedOn(localNow.toLocalDate());
          preferencesRepository.save(stored);
        });
    if (decision.push()) {
      int count = due.size();
      webPushService.sendNotification(
          user.getId(),
          "Plant care reminder",
          "You have " + count + " plant" + (count == 1 ? "" : "s") + " to care for today");
    }
    return true;
  }

  private static ZoneId zoneOf(ReminderPreferences prefs) {
    try {
      return ZoneId.of(prefs.getTimeZone());
    } catch (DateTimeException e) {
      return ZoneId.of("UTC");
    }
  }
}
