package com.plantpal.identification.dto;

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
public class ActionPlanDto {

  private String type;
  private Integer frequencyDays;
  private List<TreatmentStepDto> steps;
  private DiagramDto diagram;
}
