package com.plantpal.identification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CureAdviceResponse {

  @lombok.Setter private Long sectionId;
  private String advice;
  private ActionPlanDto actionPlan;
  private String reasoningModelUsed;
}
