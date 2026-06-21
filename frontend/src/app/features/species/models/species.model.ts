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
}
