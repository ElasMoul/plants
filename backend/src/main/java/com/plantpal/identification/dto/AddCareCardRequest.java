package com.plantpal.identification.dto;

import jakarta.validation.constraints.NotBlank;
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
public class AddCareCardRequest {

  @NotBlank private String regionLabel;

  @NotBlank private String adviceText;

  private ActionPlanDto actionPlan;

  // Which reasoning model actually generated adviceText/actionPlan — threaded through from the
  // /cure-advice response so the saved care card's "powered by" badge reflects reality instead of
  // assuming DeepSeek. Nullable for older/direct API callers; addCareCard() falls back when absent.
  private String reasoningModelUsed;
}
