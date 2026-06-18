package com.plantpal.reminder.service;

import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import java.time.Instant;
import java.util.List;

public interface ReminderService {

  ReminderResponse createReminder(CreateReminderRequest request, Long userId);

  List<ReminderResponse> getUserReminders(Long userId);

  ReminderResponse completeReminder(Long id, Long userId);

  void deleteReminder(Long id, Long userId);

  Instant calculateNextDueAt(Instant lastDone, int frequencyDays);
}
