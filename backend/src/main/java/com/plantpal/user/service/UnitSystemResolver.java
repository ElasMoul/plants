package com.plantpal.user.service;

import com.plantpal.user.entity.UnitSystem;
import java.util.Locale;
import java.util.Set;

/** Resolves the effective unit system: stored choice, else region default, else metric. */
public final class UnitSystemResolver {

  private static final Set<String> IMPERIAL_COUNTRIES = Set.of("US", "LR", "MM");

  private UnitSystemResolver() {}

  public static UnitSystem resolve(UnitSystem stored, String regionCountry) {
    if (stored != null) {
      return stored;
    }
    if (regionCountry != null
        && IMPERIAL_COUNTRIES.contains(regionCountry.toUpperCase(Locale.ROOT))) {
      return UnitSystem.IMPERIAL;
    }
    return UnitSystem.METRIC;
  }
}
