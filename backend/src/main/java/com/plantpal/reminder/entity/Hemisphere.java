package com.plantpal.reminder.entity;

import java.util.Locale;
import java.util.Set;

/** Hemisphere of the user's region; decides which half-year is the growing season (PLA-108). */
public enum Hemisphere {
  NORTHERN,
  SOUTHERN;

  // ISO 3166-1 alpha-2 codes of countries lying (almost) wholly south of the equator. Countries
  // straddling the equator are left out and resolve to the northern default.
  private static final Set<String> SOUTHERN_COUNTRIES =
      Set.of(
          "AO", "AR", "AU", "BI", "BO", "BR", "BW", "CK", "CL", "FJ", "KM", "LS", "MG", "MU", "MW",
          "MZ", "NA", "NC", "NZ", "PE", "PF", "PG", "PY", "RE", "RW", "SB", "SC", "SH", "SZ", "TL",
          "TO", "TV", "TZ", "UY", "VU", "WS", "YT", "ZA", "ZM", "ZW");

  /** Returns the hemisphere for a country code, or {@code null} when no country is known. */
  public static Hemisphere fromCountry(String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return null;
    }
    return SOUTHERN_COUNTRIES.contains(countryCode.trim().toUpperCase(Locale.ROOT))
        ? SOUTHERN
        : NORTHERN;
  }
}
