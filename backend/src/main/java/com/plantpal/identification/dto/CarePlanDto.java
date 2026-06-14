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
public class CarePlanDto {

  private int wateringFrequencyDays;
  private int fertilizingFrequencyDays;
  private int repottingFrequencyMonths;
  private List<CareCardDto> careCards;
  private List<String> beginnerWarnings;
}
