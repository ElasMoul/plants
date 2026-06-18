import { CareType } from './reminder.model';

export interface CareLogResponse {
  id: number;
  plantId: number;
  careType: CareType;
  notes: string | null;
  performedAt: string;
}
