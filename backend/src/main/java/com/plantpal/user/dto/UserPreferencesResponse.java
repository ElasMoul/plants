package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.VisionModelPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

  // Deprecated — superseded by visionModelPreference/reasoningModelPreference below.
  private AiModelPreference aiModelPreference;

  private VisionModelPreference visionModelPreference;
  private ReasoningModelPreference reasoningModelPreference;
}
