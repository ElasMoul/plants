package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

  private AiModelPreference aiModelPreference;
}
