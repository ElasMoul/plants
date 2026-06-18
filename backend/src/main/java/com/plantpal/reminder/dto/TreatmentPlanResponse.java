package com.plantpal.reminder.dto;

import java.util.List;
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
public class TreatmentPlanResponse {

  private Long id;
  private Long plantId;
  private String title;
  private String diagramFormat;
  private String diagramContent;
  private String status;
  private List<ReminderResponse> steps;
}
