package com.plantpal.reminder.dto;

import com.plantpal.reminder.entity.CareType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateReminderRequest {

  @NotNull private Long plantId;

  @NotNull private CareType careType;

  @NotNull
  @Min(1)
  private Integer frequencyDays;

  @NotNull private Instant firstDueAt;
}
