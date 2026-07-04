package com.plantpal.dashboard.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSummaryDto {

  private Long reminderId;
  private Long plantId;
  private String plantNickname;
  private String plantPhotoUrl;
  private String careType;
  private Instant nextDueAt;
  private int daysOverdue;
}
