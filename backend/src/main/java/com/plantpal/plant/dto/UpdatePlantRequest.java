package com.plantpal.plant.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** All fields are optional — null means "do not change". */
@Getter
@Setter
@NoArgsConstructor
public class UpdatePlantRequest {

  @jakarta.validation.constraints.Size(max = 255)
  private String nickname;

  @jakarta.validation.constraints.Size(max = 255)
  private String species;

  @jakarta.validation.constraints.Size(max = 255)
  private String commonName;

  @jakarta.validation.constraints.Size(max = 2048)
  private String photoUrl;

  @jakarta.validation.constraints.Size(max = 255)
  private String location;

  @jakarta.validation.constraints.Size(max = 10000)
  private String notes;

  private LocalDate acquiredAt;
}
