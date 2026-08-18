/**
 * The subset of PlantPal backend DTOs the atlas world assembles from — the
 * mission's round-1 spine (coverage-scope.json): plants, species,
 * identifications (the one async family). Dashboard/care/reminders families are
 * deliberately deferred by the scope and render as deferred panels.
 */

export interface PlantDto {
  id: number;
  nickname: string;
  species: string | null;
  commonName: string | null;
  healthStatus?: string;
  nextWaterDays?: number | null;
  activeTreatmentId?: number | null;
  location?: string | null;
}

export interface SpeciesDto {
  id: number;
  scientificName: string;
  commonName: string | null;
}

export interface IdentificationDto {
  id: number;
  species: string | null;
  commonName: string | null;
  healthStatus: string | null;
  status: string; // PENDING | PROCESSING | COMPLETED | FAILED (backend enum)
  createdAt: string;
  plantId?: number | null;
}

/** The signed-in user, for the account node (entry stays on the classic app). */
export interface WorldUser {
  firstName: string;
  lastName: string;
  email: string;
}

/** Everything the assembly needs, gathered from the endpoints. */
export interface WorldSources {
  plants: PlantDto[];
  species: SpeciesDto[];
  identifications: IdentificationDto[];
  user: WorldUser | null;
}
