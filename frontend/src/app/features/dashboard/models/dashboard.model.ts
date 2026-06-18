export type CareType = 'WATERING' | 'FERTILIZING' | 'REPOTTING' | 'PRUNING';
export type HealthTrend = 'IMPROVING' | 'WORSENING' | 'STABLE';

export interface HealthSummaryDto {
  totalPlants: number;
  healthyCount: number;
  issuesCount: number;
  unknownCount: number;
}

export interface ReminderSummaryDto {
  reminderId: number;
  plantId: number;
  plantNickname: string;
  plantPhotoUrl: string | null;
  careType: CareType;
  nextDueAt: string;
  daysOverdue: number;
}

export interface PlantHealthTrendDto {
  plantId: number;
  plantNickname: string;
  trend: HealthTrend;
}

export interface DashboardResponse {
  healthSummary: HealthSummaryDto;
  overdueReminders: ReminderSummaryDto[];
  todayReminders: ReminderSummaryDto[];
  healthTrends: PlantHealthTrendDto[];
}
