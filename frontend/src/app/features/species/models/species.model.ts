import { CareCardDto } from '../../identification/models/identification.model';

export interface SpeciesSummaryDto {
  speciesId: number;
  scientificName: string;
  commonName: string | null;
  imageUrl: string | null;
  plantCount: number;
  healthSummary: string;
  lastScanAt: string | null;
}

export type SpeciesStatus = 'ACTIVE' | 'NEEDS_REVIEW';
export type GenerationStatus = 'PENDING' | 'READY' | 'FAILED';

export interface SpeciesResponse {
  id: number;
  scientificName: string;
  commonName: string | null;
  description: string | null;
  careOverview: string | null;
  imageUrl: string | null;
  externalDataSource: string | null;
  status: SpeciesStatus;
  careCards: CareCardDto[];
  // Reasoning model that generated description/careOverview enrichment (T7.2 "powered by" badge).
  enrichmentModel?: string | null;
  // Botanical identity fields harvested from the confirmed PlantNet candidate (T9.A).
  family?: string | null;
  genus?: string | null;
  identitySource?: string | null;
  imageAttribution?: string | null;
  imageLicense?: string | null;
  // Status of async AI prose generation (T9.B). Frontend polls while PENDING, shows Retry on FAILED.
  descriptionStatus?: GenerationStatus | null;
  // Botanical taxonomy IDs from PlantNet/GBIF (harvested at resolve time — T8.6 / T9.A).
  gbifId?: string | null;
  powoId?: string | null;
  iucnCategory?: string | null;
}
