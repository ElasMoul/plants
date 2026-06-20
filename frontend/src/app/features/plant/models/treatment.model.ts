export type TreatmentStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED' | 'DISMISSED';

export interface TreatmentResponse {
  id: number;
  plantId: number;
  diseaseName: string;
  diseaseDescription: string | null;
  status: TreatmentStatus;
  treatmentPlanId: number | null;
  startedAt: string | null;
  completedAt: string | null;
}
