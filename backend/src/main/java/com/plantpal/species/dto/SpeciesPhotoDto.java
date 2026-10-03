package com.plantpal.species.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeciesPhotoDto {

  private Long identificationId;
  private String photoUrl;
  private Instant dateTaken;
}
