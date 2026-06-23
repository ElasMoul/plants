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
  detail?: string | null;
  diagram?: DiagramDto | null;
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
  // Which reasoning model generated actionPlan, when it came from a standalone regeneration
  // (e.g. the cure-advice "add to care plan" flow) rather than the original vision call.
  actionPlanModel?: string | null;
}

export interface CarePlanDto {
  wateringFrequencyDays: number;
  fertilizingFrequencyDays: number;
  repottingFrequencyMonths: number;
  careCards: CareCardDto[];
  beginnerWarnings: string[];
}

const AI_MODEL_LABELS: Record<string, string> = {
  DEEPSEEK: 'GPT-4o',
  GITHUB_GPT4O: 'GPT-4o',
  OLLAMA_LLAVA: 'Ollama (local)',
  PLANTNET: 'PlantNet',
};

export function aiModelLabel(model: string | null | undefined): string | null {
  if (!model) return null;
  return AI_MODEL_LABELS[model] ?? model;
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
  aiModelUsed?: string | null;
  // Split out from aiModelUsed (T7.1/T7.2) — which model handled the vision pass (species +
  // health + initial care plan) vs. any later reasoning-model work on this identification.
  visionModelUsed?: string | null;
  reasoningModelUsed?: string | null;
  speciesId: number | null;
}

export interface AnalyzeEmitPayload {
  images: File[];
  organs: string[];
  plantId?: number;
  speciesId?: number;
}

export interface PlantNetReferenceImageDto {
  smallUrl: string;
  mediumUrl: string;
  author: string;
  license: string;
  citation: string;
}

export interface PlantNetCandidateDto {
  score: number;
  scientificName: string;
  genus: string;
  family: string;
  commonNames: string[];
  gbifId: string | null;
  powoId: string | null;
  iucnCategory: string | null;
  referenceImages: PlantNetReferenceImageDto[];
}

export interface SpeciesMatchDto {
  matched: boolean;
  speciesId: number | null;
  scientificName: string;
  commonName: string;
  // PlantNet v2 ranked candidates (T8.2/T8.3) — null/empty for non-PlantNet identifications
  candidates: PlantNetCandidateDto[] | null;
  bestMatch: string | null;
  switchToProject: string | null;
  autoConfirmable: boolean;
  plantNetVersion: string | null;
}

export interface PlantSummaryDto {
  id: number;
  nickname: string;
  photoUrl: string | null;
}

export interface PlantMatchDto {
  candidatePlants: PlantSummaryDto[];
}

export interface SaveAsNewEvent {
  scientificName: string;
  commonName: string;
}
