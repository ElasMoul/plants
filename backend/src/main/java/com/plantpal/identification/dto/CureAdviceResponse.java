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

  private String advice;
  private ActionPlanDto actionPlan;
  private String reasoningModelUsed;
}
