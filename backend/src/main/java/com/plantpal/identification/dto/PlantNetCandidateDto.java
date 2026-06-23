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
public class PlantNetCandidateDto {
  private double score;
  private String scientificName;
  private String genus;
  private String family;
  private List<String> commonNames;
  private String gbifId;
  private String powoId;
  private String iucnCategory;
  private List<PlantNetReferenceImageDto> referenceImages;
}
