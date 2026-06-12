package com.plantpal.plant.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePlantRequest {

  @NotBlank private String nickname;

  private String location;

  private String notes;

  private LocalDate acquiredAt;
}
