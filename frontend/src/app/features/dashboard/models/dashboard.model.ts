// Mirrors backend CareType (T3.4) — same 10 values as reminder/identification's CareType/CareCardType
export type CareType =
  | 'WATERING'
  | 'LIGHT'
  | 'HUMIDITY'
  | 'TEMPERATURE'
  | 'FERTILIZING'
  | 'REPOTTING'
  | 'PRUNING'
  | 'PEST'
  | 'SEASONAL'
  | 'BEGINNER_TIP';
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

export interface RecentScanDto {
  identificationId: number;
  plantId: number | null;
  plantNickname: string | null;
  photoUrl: string | null;
  healthStatus: string | null;
  createdAt: string;
}

export interface DashboardResponse {
  healthSummary: HealthSummaryDto;
  overdueReminders: ReminderSummaryDto[];
  todayReminders: ReminderSummaryDto[];
  healthTrends: PlantHealthTrendDto[];
  recentScans: RecentScanDto[];
  speciesCount: number;
}
