export type Placement = 'INDOOR' | 'OUTDOOR' | 'GREENHOUSE';
export type LightExposure = 'LOW' | 'MEDIUM' | 'BRIGHT' | 'DIRECT_SUN';

export const PLACEMENTS: readonly Placement[] = ['INDOOR', 'OUTDOOR', 'GREENHOUSE'];
export const LIGHT_EXPOSURES: readonly LightExposure[] = ['LOW', 'MEDIUM', 'BRIGHT', 'DIRECT_SUN'];

export type PlantStatus = 'ACTIVE' | 'ARCHIVED';

export interface SaveIdentificationAsPlantRequest {
  identificationId: number;
  nickname: string;
  location?: string;
}

export interface CreatePlantRequest {
  nickname: string;
  species?: string;
  commonName?: string;
  photoUrl?: string;
  location?: string;
  notes?: string;
  acquiredAt?: string; // YYYY-MM-DD
  placement?: Placement;
  lightExposure?: LightExposure;
}

export interface UpdatePlantRequest {
  nickname?: string;
  species?: string;
  commonName?: string;
  photoUrl?: string;
  location?: string;
  notes?: string;
  acquiredAt?: string; // YYYY-MM-DD
  placement?: Placement;
  lightExposure?: LightExposure;
}

export type HealthStatus = 'HEALTHY' | 'ISSUES_DETECTED' | 'UNKNOWN';

export interface PlantResponse {
  id: number;
  userId: number;
  nickname: string;
  species: string | null;
  commonName: string | null;
  photoUrl: string | null;
  location: string | null;
  notes: string | null;
  status: PlantStatus;
  acquiredAt: string | null;
  createdAt: string;
  updatedAt: string;
  healthStatus?: HealthStatus;
  nextWaterDays?: number | null;
  lastScanAt?: string | null;
  activeTreatmentId?: number | null;
  placement?: Placement | null;
  lightExposure?: LightExposure | null;
  groupId?: number | null;
}
