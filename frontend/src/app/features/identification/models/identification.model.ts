export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type IdentificationStatus = 'PENDING' | 'COMPLETED' | 'FAILED';
export type HealthStatus = 'HEALTHY' | 'ISSUES_DETECTED' | 'UNKNOWN';
export type AnnotationRegionType = 'PLANT' | 'DISEASE' | 'HEALTHY_AREA';

export interface AnnotationBoundingBox {
  xPct: number;      // percentage 0–100
  yPct: number;
  widthPct: number;
  heightPct: number;
}

export interface PolygonPoint {
  xPct: number;
  yPct: number;
}

export interface AnnotationRegion {
  type: AnnotationRegionType;
  label: string;
  confidence: string;
  boundingBox?: AnnotationBoundingBox;
  polygon?: PolygonPoint[];
}

export interface SavePreviewEditEvent {
  nickname: string;
  location: string;
}

export interface IdentificationPendingResponse {
  identificationId: number;
  status: string;
}

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

export type ActionPlanType = 'ROUTINE' | 'TREATMENT';

export interface DiagramDto {
  format: 'MERMAID';
  content: string;
}

export interface TreatmentStepDto {
  order: number;
  instruction: string;
  dueOffsetDays: number;
}

export interface ActionPlanDto {
  type: ActionPlanType;
  frequencyDays?: number;
  steps?: TreatmentStepDto[];
  diagram?: DiagramDto | null;
}

export interface CareCardDto {
  type: CareCardType;
  title: string;
  icon: string;
  summary: string;
  detail: string;
  urgency: 'LOW' | 'MEDIUM' | 'HIGH';
  seasonalVariation: string | null;
  actionPlan?: ActionPlanDto | null;
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
  healthStatus: HealthStatus | null;
  healthNotes: string | null;
  status: IdentificationStatus;
  topResults: PlantNetResult[];
  photoUrl: string;
  carePlan: CarePlanDto | null;
  annotationRegions: AnnotationRegion[] | null;
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
