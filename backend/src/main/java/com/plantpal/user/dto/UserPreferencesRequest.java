package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.VisionModelPreference;
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
public class UserPreferencesRequest {

  // Deprecated — superseded by visionModelPreference/reasoningModelPreference below.
  @NotNull private AiModelPreference aiModelPreference;

  // Nullable: old frontend callers don't send these yet (additive this phase) — when
  // omitted, the user's existing stored preference is left untouched.
  private VisionModelPreference visionModelPreference;
  private ReasoningModelPreference reasoningModelPreference;
}
