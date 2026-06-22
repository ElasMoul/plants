package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.VisionModelPreference;
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

  // Deprecated — superseded by visionModelPreference/reasoningModelPreference below. Nullable:
  // the current frontend never sends this field anymore (it only sends the two below), so a
  // @NotNull here made every save fail with a 400. When omitted, the user's existing stored
  // value is left untouched (same as the two fields below).
  private AiModelPreference aiModelPreference;

  // Nullable: old frontend callers don't send these yet (additive this phase) — when
  // omitted, the user's existing stored preference is left untouched.
  private VisionModelPreference visionModelPreference;
  private ReasoningModelPreference reasoningModelPreference;
}
