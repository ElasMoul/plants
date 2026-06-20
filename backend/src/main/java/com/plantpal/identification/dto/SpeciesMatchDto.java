package com.plantpal.identification.dto;

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
}
