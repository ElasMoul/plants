package com.plantpal.user.dto;

import com.plantpal.user.entity.AiModelPreference;
import com.plantpal.user.entity.ClimateZone;
import com.plantpal.user.entity.ReasoningModelPreference;
import com.plantpal.user.entity.VisionModelPreference;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

  private String language;

  // Profile (PLA-24). pendingEmail is an unconfirmed email change; the login address stays
  // `email` until the link sent to pendingEmail is used.
  private String firstName;
  private String lastName;
  private String email;
  private String pendingEmail;

  // Deprecated — superseded by visionModelPreference/reasoningModelPreference below.
  private AiModelPreference aiModelPreference;

  private VisionModelPreference visionModelPreference;
  private ReasoningModelPreference reasoningModelPreference;

  // Keyed by enum name (e.g. "ANTHROPIC_CLAUDE") -> whether that option is actually usable right
  // now (provider configured with an API key/host). The frontend disables unavailable options
  // rather than letting a user pick a model that will 401/503 on first call.
  private Map<String, Boolean> visionModelAvailability;
  private Map<String, Boolean> reasoningModelAvailability;

  private Map<String, Boolean> visionModelVisibility;
  private Map<String, Boolean> reasoningModelVisibility;

  // PlantNet flora and common-name language (T8.4).
  private String plantnetProject;
  private String plantnetLang;

  // Self-declared business/professional tier (D027, PP-086). Non-null in practice.
  private Boolean businessTier;

  // Coarse region (PLA-107); null when unset.
  private String regionCountry;
  private ClimateZone regionClimate;

  // Whether the account's email address has been verified (PLA-26); read-only.
  private boolean emailVerified;
}
