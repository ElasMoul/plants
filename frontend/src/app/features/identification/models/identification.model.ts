export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type IdentificationStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export type CareCardType =
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

export interface CareCardDto {
  type: CareCardType;
  title: string;
  icon: string;
  summary: string;
  detail: string;
  urgency: 'LOW' | 'MEDIUM' | 'HIGH';
  seasonalVariation: string | null;
}

export interface CarePlanDto {
  wateringFrequencyDays: number;
  fertilizingFrequencyDays: number;
  repottingFrequencyMonths: number;
  careCards: CareCardDto[];
  beginnerWarnings: string[];
}

export function getConfidenceLevel(score: number): ConfidenceLevel {
  if (score >= 0.80) return 'HIGH';
  if (score >= 0.50) return 'MEDIUM';
  return 'LOW';
}

export interface PlantNetSpecies {
  scientificNameWithoutAuthor: string;
  commonNames: string[];
  genus: { scientificNameWithoutAuthor: string };
  family: { scientificNameWithoutAuthor: string };
}

export interface PlantNetResult {
  score: number;
  species: PlantNetSpecies;
}

export interface IdentificationResponse {
  id: number;
  plantId: number | null;
  scientificName: string;
  commonName: string;
  confidence: number;
  status: IdentificationStatus;
  topResults: PlantNetResult[];
  photoUrl: string;
  carePlan: CarePlanDto | null;
  createdAt: string;
}

export interface AnalyzeEmitPayload {
  images: File[];
  organs: string[];
  plantId?: number;
}

export interface SaveAsNewEvent {
  scientificName: string;
  commonName: string;
}
