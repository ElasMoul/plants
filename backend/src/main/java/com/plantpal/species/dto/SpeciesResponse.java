package com.plantpal.species.dto;

import com.plantpal.species.entity.SpeciesStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpeciesResponse {

  private Long id;

  private String scientificName;

  private String commonName;

  private String description;

  private String careOverview;

  private String imageUrl;

  private String externalDataSource;

  private SpeciesStatus status;
}
