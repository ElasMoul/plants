package com.plantpal.identification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekPlantResult {
  private String species;
  private String commonName;
  private String confidence;
  private String healthStatus;
  private String healthNotes;
  private CarePlanDto carePlan;
}
