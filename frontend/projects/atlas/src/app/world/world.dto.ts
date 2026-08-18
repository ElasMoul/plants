/**
 * The subset of PlantPal backend DTOs the atlas world assembles from. Mirrors the
 * real response shapes (dashboard/plants/species) — atlas maps these into the
 * world graph client-side, so no backend change is needed (Phase D).
 */

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
  careType: string;
  nextDueAt: string;
  daysOverdue: number;
}

export interface DashboardDto {
  healthSummary: HealthSummaryDto;
  overdueReminders: ReminderSummaryDto[];
  todayReminders: ReminderSummaryDto[];
  speciesCount: number;
}

export interface PlantDto {
  id: number;
  nickname: string;
  species: string | null;
  commonName: string | null;
  healthStatus?: string;
  nextWaterDays?: number | null;
  activeTreatmentId?: number | null;
}

export interface SpeciesDto {
  id: number;
  scientificName: string;
  commonName: string | null;
}

/** Everything the assembly needs, gathered from the endpoints. */
export interface WorldSources {
  dashboard: DashboardDto;
  plants: PlantDto[];
  species: SpeciesDto[];
}
