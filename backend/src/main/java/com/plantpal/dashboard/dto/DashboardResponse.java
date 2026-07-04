package com.plantpal.dashboard.dto;

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
public class DashboardResponse {

  private HealthSummaryDto healthSummary;
  private List<ReminderSummaryDto> overdueReminders;
  private List<ReminderSummaryDto> todayReminders;
  private List<PlantHealthTrendDto> healthTrends;
  private List<RecentScanDto> recentScans;
  private int speciesCount;
}
