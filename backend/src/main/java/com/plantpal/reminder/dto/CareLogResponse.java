package com.plantpal.reminder.dto;

import com.plantpal.reminder.entity.CareType;
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
public class CareLogResponse {

  private Long id;
  private Long plantId;
  private String plantNickname;
  private CareType careType;
  private String notes;
  private Instant performedAt;
}
