package com.plantpal.reminder.service;

import com.plantpal.reminder.dto.ReminderPreferencesRequest;
import com.plantpal.reminder.dto.ReminderPreferencesResponse;
import com.plantpal.reminder.entity.ReminderPreferences;

public interface ReminderPreferencesService {

  /** The user's settings, or the defaults when they never saved any. */
  ReminderPreferencesResponse get(Long userId);

  /**
   * Saves the settings. Choosing EMAIL or BOTH is an explicit opt-in and clears an earlier email
   * unsubscribe.
   *
   * @throws com.plantpal.shared.exception.ValidationException on a bad time zone or quiet hours
   */
  ReminderPreferencesResponse update(Long userId, ReminderPreferencesRequest request);

  /** The stored row, created with a fresh unsubscribe token when the user has none yet. */
  ReminderPreferences getOrCreate(Long userId);

  /**
   * Unsubscribes the owner of {@code token} from reminder email and cancels their queued digests.
   *
   * @return false if the token matches no one; nothing changes then
   */
  boolean unsubscribeByToken(String token);
}
