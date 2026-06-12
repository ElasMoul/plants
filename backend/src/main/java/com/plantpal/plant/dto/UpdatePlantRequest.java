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

  private String nickname;

  private String species;

  private String commonName;

  private String photoUrl;

  private String location;

  private String notes;

  private LocalDate acquiredAt;
}
