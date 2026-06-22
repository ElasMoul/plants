export type TreatmentStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'DISMISSED';

export interface TreatmentResponse {
  id: number;
  plantId: number;
  identificationId: number | null;
  diseaseName: string;
  diseaseDescription: string | null;
  status: TreatmentStatus;
  treatmentPlanId: number | null;
  startedAt: string | null;
  completedAt: string | null;
  // Which reasoning model generated diseaseDescription / the crafted plan (T7.2 "powered by" badge).
  diseaseDescriptionModel?: string | null;
  treatmentPlanModel?: string | null;
}
