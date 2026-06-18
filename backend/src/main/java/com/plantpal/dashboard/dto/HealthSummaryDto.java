package com.plantpal.dashboard.dto;

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
public class HealthSummaryDto {

  private int totalPlants;
  private int healthyCount;
  private int issuesCount;
  private int unknownCount;
}
