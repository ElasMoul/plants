package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
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

  @NotNull private AiModelPreference aiModelPreference;
}
