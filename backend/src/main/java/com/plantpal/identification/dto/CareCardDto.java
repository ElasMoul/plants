package com.plantpal.identification.dto;

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
public class CareCardDto {

  private String type;
  private String title;
  private String icon;
  private String summary;
  private String detail;
  private String urgency;
  private String seasonalVariation;
  private ActionPlanDto actionPlan;
}
