export type CareType = 'WATERING' | 'FERTILIZING' | 'REPOTTING' | 'PRUNING';

export interface ReminderResponse {
  id: number;
  plantId: number;
  plantNickname: string;
  plantPhotoUrl: string | null;
  careType: CareType;
  frequencyDays: number;
  nextDueAt: string;
  enabled: boolean;
}

export interface CreateReminderRequest {
  plantId: number;
  careType: CareType;
  frequencyDays: number;
  firstDueAt: string; // ISO date
}
