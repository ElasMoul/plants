package com.plantpal.reminder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReminderIntervalRequest {

  /** Days between care actions; null clears the override. */
  @Min(1)
  @Max(365)
  private Integer frequencyDays;
}
