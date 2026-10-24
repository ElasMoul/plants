package com.plantpal.reminder.service;

import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Decides, for one user at one moment, whether today's reminder goes out by push, email or not at
 * all. Pure: no I/O, so every rule is unit-tested without Spring.
 */
public final class ReminderDeliveryPolicy {

  /** Local time of day from which the daily notification may be sent. */
  public static final LocalTime DAILY_SEND_TIME = LocalTime.of(8, 0);

  /** What to send; both false means send nothing. */
  public record Decision(boolean push, boolean email) {

    public static final Decision NONE = new Decision(false, false);

    public boolean any() {
      return push || email;
    }
  }

  private ReminderDeliveryPolicy() {}

  /**
   * @param prefs the user's settings; {@link ReminderPreferences#defaults} for a user with no row
   * @param hasActivePushSubscription whether the user has an enabled push subscription
   * @param localNow the current moment in the user's own time zone
   */
  public static Decision decide(
      ReminderPreferences prefs, boolean hasActivePushSubscription, ZonedDateTime localNow) {
    LocalTime time = localNow.toLocalTime();
    if (time.isBefore(DAILY_SEND_TIME)
        || isQuiet(prefs.getQuietHoursStart(), prefs.getQuietHoursEnd(), time)
        || localNow.toLocalDate().equals(prefs.getLastNotifiedOn())) {
      return Decision.NONE;
    }
    ReminderChannel channel = prefs.getChannel();
    boolean wantsPush = channel == ReminderChannel.PUSH || channel == ReminderChannel.BOTH;
    boolean wantsEmail = channel == ReminderChannel.EMAIL || channel == ReminderChannel.BOTH;
    // Push-only users with no way to receive push get the digest instead (the fallback).
    boolean emailFallback = channel == ReminderChannel.PUSH && !hasActivePushSubscription;
    boolean push = wantsPush && hasActivePushSubscription;
    boolean email = !prefs.isEmailUnsubscribed() && (wantsEmail || emailFallback);
    return new Decision(push, email);
  }

  /** Start is inclusive, end exclusive; a window with start after end crosses midnight. */
  public static boolean isQuiet(LocalTime start, LocalTime end, LocalTime time) {
    if (start == null || end == null || start.equals(end)) {
      return false;
    }
    if (start.isBefore(end)) {
      return !time.isBefore(start) && time.isBefore(end);
    }
    return !time.isBefore(start) || time.isBefore(end);
  }
}
