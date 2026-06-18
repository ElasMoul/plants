package com.plantpal.reminder.scheduler;

import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.service.WebPushService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

  private final ReminderRepository reminderRepository;
  private final WebPushService webPushService;
  private final Clock clock;

  public ReminderScheduler(
      ReminderRepository reminderRepository, WebPushService webPushService, Clock clock) {
    this.reminderRepository = reminderRepository;
    this.webPushService = webPushService;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 8 * * *")
  public void sendDueReminderNotifications() {
    List<Reminder> dueReminders = reminderRepository.findAllDue(Instant.now(clock));
    Map<Long, List<Reminder>> dueByUserId =
        dueReminders.stream().collect(Collectors.groupingBy(Reminder::getUserId));

    int sent = 0;
    int failed = 0;
    for (Map.Entry<Long, List<Reminder>> entry : dueByUserId.entrySet()) {
      Long userId = entry.getKey();
      int count = entry.getValue().size();
      try {
        webPushService.sendNotification(
            userId,
            "Plant care reminder",
            "You have " + count + " plant" + (count == 1 ? "" : "s") + " to care for today");
        sent++;
      } catch (Exception e) {
        failed++;
        log.warn(
            "Failed to send reminder notification: userId={}, error={}", userId, e.getMessage());
      }
    }

    log.info(
        "Reminder notifications processed: sent={}, failed={}, usersWithDueReminders={},"
            + " totalDueReminders={}",
        sent,
        failed,
        dueByUserId.size(),
        dueReminders.size());
  }
}
