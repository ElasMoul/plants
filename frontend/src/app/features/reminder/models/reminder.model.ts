// Mirrors backend CareType exactly (T3.4) — same 10 values as identification's CareCardType,
// since a routine actionPlan can attach a reminder to any care-card type, not just the original 4.
export type CareType =
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

export interface ReminderResponse {
  id: number;
  plantId: number;
  plantNickname: string;
  plantPhotoUrl: string | null;
  careType: CareType;
  frequencyDays: number;
  nextDueAt: string;
  enabled: boolean;
  treatmentPlanId?: number | null;
  treatmentPlanTitle?: string | null;
  stepOrder?: number | null;
  recurring: boolean;
  // Free-text step description — only set for treatment-plan steps, null for routine reminders
  instruction?: string | null;
  completedAt?: string | null;
}

export interface CreateReminderRequest {
  plantId: number;
  careType: CareType;
  frequencyDays: number;
  firstDueAt: string; // ISO date
}
