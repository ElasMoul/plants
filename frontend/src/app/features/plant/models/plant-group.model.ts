export interface PlantGroup {
  id: number;
  name: string;
  plantCount: number;
  createdAt: string;
}

export type GroupCareAction = 'WATERED' | 'FED';

export interface GroupCareLog {
  id: number;
  plantId: number;
  plantNickname: string | null;
  careType: string;
  performedAt: string;
}