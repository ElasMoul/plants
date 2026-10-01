package com.plantpal.species.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantPhotosDto {

  private Long plantId;
  private String nickname;
  private List<SpeciesPhotoDto> photos;
}
