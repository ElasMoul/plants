import { CareType } from './reminder.model';

export interface CareLogResponse {
  id: number;
  plantId: number;
  plantNickname?: string | null;
  careType: CareType;
  notes: string | null;
  performedAt: string;
}
