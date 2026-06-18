package com.plantpal.reminder.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MarkCareDoneRequest {

  @NotNull private Long reminderId;

  private String notes;
}
