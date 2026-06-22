package com.plantpal.species.dto;

import com.plantpal.identification.dto.CareCardDto;
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

  private SpeciesStatus status;

  // Parsed from Species.careCards (raw JSON) in SpeciesServiceImpl -- never trust the AI
  // response shape directly, same defensive-parse philosophy as IdentificationServiceImpl's
  // parseCarePlan(). Null/empty when enrichment hasn't run yet, failed, or returned no cards.
  private List<CareCardDto> careCards;
}
