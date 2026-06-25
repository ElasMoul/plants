package com.plantpal.species.dto;

import com.plantpal.identification.dto.CareCardDto;
import com.plantpal.shared.entity.GenerationStatus;
import com.plantpal.species.entity.SpeciesStatus;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class SpeciesResponse {

  private Long id;

  private String scientificName;

  private String commonName;

  private String description;

  private String careOverview;

  private String imageUrl;

  private String externalDataSource;

  private String enrichmentModel;

  private String gbifId;

  private String powoId;

  private String iucnCategory;

  private SpeciesStatus status;

  // Botanical identity fields harvested from the confirmed PlantNet candidate (T9.A).
  private String family;

  private String genus;

  private String identitySource;

  private String imageAttribution;

  private String imageLicense;

  // Status of async AI prose generation (T9.B). Frontend polls while PENDING, shows Retry on FAILED.
  private GenerationStatus descriptionStatus;

  // Parsed from Species.careCards (raw JSON) in SpeciesServiceImpl -- never trust the AI
  // response shape directly, same defensive-parse philosophy as IdentificationServiceImpl's
  // parseCarePlan(). Null/empty when enrichment hasn't run yet, failed, or returned no cards.
  private List<CareCardDto> careCards;
}
