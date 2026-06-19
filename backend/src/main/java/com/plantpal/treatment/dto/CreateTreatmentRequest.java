package com.plantpal.treatment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateTreatmentRequest {

  @NotNull private Long plantId;

  @NotNull private Long identificationId;

  @NotBlank private String diseaseName;
}
