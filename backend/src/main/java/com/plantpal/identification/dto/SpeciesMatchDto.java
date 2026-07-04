package com.plantpal.identification.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesMatchDto {

  private boolean matched;
  private Long speciesId;
  private String scientificName;
  private String commonName;

  // PlantNet v2 ranked candidates — populated when PlantNet was called (T8.2/T8.3).
  // Null/empty when the identification used a non-PlantNet vision model.
  private List<PlantNetCandidateDto> candidates;
  private String bestMatch;
  private String switchToProject;
  private boolean autoConfirmable;
  private String plantNetVersion;
}
