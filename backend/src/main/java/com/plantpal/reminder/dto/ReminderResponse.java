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
public class ReminderResponse {

  private Long id;
  private Long plantId;
  private String plantNickname;
  private String plantPhotoUrl;
  private CareType careType;
  private int frequencyDays;
  private Instant nextDueAt;
  private boolean enabled;
  private boolean recurring;
  private Long treatmentPlanId;
  private String treatmentPlanTitle;
  private Integer stepOrder;
  private String instruction;
  private Instant completedAt;
  private String stepDetail;
  private String stepDiagramFormat;
  private String stepDiagramContent;
}
