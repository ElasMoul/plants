package com.plantpal.reminder.service;

import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.entity.Reminder;
import java.time.Instant;
import java.util.List;

public interface ReminderService {

  ReminderResponse createReminder(CreateReminderRequest request, Long userId);

  List<ReminderResponse> getUserReminders(Long userId);

  ReminderResponse completeReminder(Long id, Long userId);

  void deleteReminder(Long id, Long userId);

  Instant calculateNextDueAt(Instant lastDone, int frequencyDays);

  /**
   * Applies the effect of a completed care action to a reminder and persists it. Recurring
   * reminders are rescheduled; one-time (treatment step) reminders are disabled, and — if that was
   * the last enabled step of their treatment plan — the plan is marked COMPLETED.
   *
   * <p>Does NOT persist the CareLog itself; the caller owns that since notes differ per call site.
   */
  void applyCompletionToReminder(Reminder reminder, Instant performedAt);
}
