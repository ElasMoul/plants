package com.plantpal.reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's reminder delivery settings. Rows are created lazily; {@link #defaults} describes what a
 * missing row means (push only, no quiet hours, UTC, not unsubscribed).
 */
@Entity
@Table(name = "reminder_preferences")
@Getter
@Setter
@NoArgsConstructor
public class ReminderPreferences {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 10)
  private ReminderChannel channel = ReminderChannel.PUSH;

  @Column(name = "quiet_hours_start")
  private LocalTime quietHoursStart;

  @Column(name = "quiet_hours_end")
  private LocalTime quietHoursEnd;

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone = "UTC";

  @Column(name = "email_unsubscribed", nullable = false)
  private boolean emailUnsubscribed;

  @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 64)
  private String unsubscribeToken;

  @Column(name = "last_notified_on")
  private LocalDate lastNotifiedOn;

  /** The behaviour of a user with no stored row; not persisted and without a token. */
  public static ReminderPreferences defaults(Long userId) {
    ReminderPreferences prefs = new ReminderPreferences();
    prefs.setUserId(userId);
    return prefs;
  }
}
