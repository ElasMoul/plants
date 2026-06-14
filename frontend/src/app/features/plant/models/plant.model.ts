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
}

export interface UpdatePlantRequest {
  nickname?: string;
  species?: string;
  commonName?: string;
  photoUrl?: string;
  location?: string;
  notes?: string;
  acquiredAt?: string; // YYYY-MM-DD
}

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
}
