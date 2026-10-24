package com.plantpal.reminder.dto;

import com.plantpal.reminder.entity.ReminderChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReminderPreferencesRequest {

  @NotNull private ReminderChannel channel;

  /** {@code HH:mm}; set together with {@link #quietHoursEnd}, or both null. */
  @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "must be HH:mm")
  private String quietHoursStart;

  @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "must be HH:mm")
  private String quietHoursEnd;

  /** IANA zone id such as {@code Europe/Paris}. */
  @NotNull private String timeZone;
}
