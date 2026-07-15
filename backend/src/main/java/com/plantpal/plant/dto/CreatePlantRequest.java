package com.plantpal.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePlantRequest {

  @NotBlank
  @Schema(example = "Monty")
  private String nickname;

  @Schema(example = "Living room windowsill")
  private String location;

  @Schema(example = "Bought from the farmers market")
  private String notes;

  @Schema(example = "2024-01-15")
  private LocalDate acquiredAt;
}
