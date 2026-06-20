export interface SpeciesSummaryDto {
  speciesId: number;
  scientificName: string;
  commonName: string | null;
  imageUrl: string | null;
  plantCount: number;
  healthSummary: string;
}
