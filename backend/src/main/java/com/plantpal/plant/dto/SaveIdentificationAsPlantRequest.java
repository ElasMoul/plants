package com.plantpal.plant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaveIdentificationAsPlantRequest {

  @NotNull(message = "identificationId is required")
  private Long identificationId;

  private String nickname;
  private String location;
}
