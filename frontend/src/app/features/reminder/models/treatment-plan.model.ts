import { ActionPlanDto } from '../../identification/models/identification.model';
import { ReminderResponse } from './reminder.model';

export type TreatmentPlanStatus = 'ACTIVE' | 'COMPLETED' | 'ABANDONED';

export interface TreatmentPlanResponse {
  id: number;
  plantId: number;
  title: string;
  diagramFormat: 'MERMAID' | null;
  diagramContent: string | null;
  status: TreatmentPlanStatus;
  steps: ReminderResponse[];
}

export interface CreateTreatmentPlanRequest {
  plantId: number;
  title: string;
  sourceCareCardType: string;
  actionPlan: ActionPlanDto;
}
