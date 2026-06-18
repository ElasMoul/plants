package com.plantpal.reminder.dto;

import com.plantpal.identification.dto.ActionPlanDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TreatmentPlanRequest {

  @NotNull private Long plantId;

  @NotBlank private String title;

  private String sourceCareCardType;

  @NotNull @Valid private ActionPlanDto actionPlan;
}
