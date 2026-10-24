package com.plantpal.reminder;

import com.plantpal.plant.entity.Placement;
import com.plantpal.reminder.entity.CareSeason;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Hemisphere;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Pure, stateless seasonal/placement adjustment of recurring care intervals (PLA-108). Only
 * WATERING and FERTILIZING are adjusted; a user override always wins over the calculation.
 */
public final class CareIntervalCalculator {

  private static final double WATERING_RESTING_FACTOR = 1.5;
  private static final double FERTILIZING_RESTING_FACTOR = 2.0;
  private static final double OUTDOOR_WATERING_FACTOR = 0.75;

  private CareIntervalCalculator() {}

  /**
   * Result of a calculation. {@code season} and {@code reason} are null for care types that are not
   * adjusted.
   */
  public record CareInterval(int days, CareSeason season, String reason) {}

  public static CareInterval calculate(
      CareType careType,
      int baseDays,
      LocalDate date,
      Hemisphere hemisphere,
      Placement placement,
      Integer overrideDays) {
    if (careType != CareType.WATERING && careType != CareType.FERTILIZING) {
      return new CareInterval(baseDays, null, null);
    }
    Hemisphere effective = hemisphere != null ? hemisphere : Hemisphere.NORTHERN;
    CareSeason season = seasonOf(date, effective);
    String where =
        hemisphere == null ? "northern hemisphere assumed" : label(effective) + " hemisphere";

    if (overrideDays != null) {
      return new CareInterval(
          overrideDays, season, "User-set interval of " + overrideDays + " days.");
    }

    boolean resting = season == CareSeason.RESTING;
    boolean watering = careType == CareType.WATERING;
    double factor =
        resting ? (watering ? WATERING_RESTING_FACTOR : FERTILIZING_RESTING_FACTOR) : 1.0;
    if (watering && placement == Placement.OUTDOOR) {
      factor *= OUTDOOR_WATERING_FACTOR;
    }
    int days = Math.max(1, (int) Math.round(baseDays * factor));
    String reason =
        (resting ? "Resting season" : "Growing season")
            + " ("
            + where
            + "), "
            + (placement == null ? "placement not set" : label(placement) + " plant")
            + ": "
            + (days == baseDays ? "standard interval of " : "adjusted from " + baseDays + " to ")
            + days
            + " days.";
    return new CareInterval(days, season, reason);
  }

  public static CareSeason seasonOf(LocalDate date, Hemisphere hemisphere) {
    int month = date.getMonthValue();
    boolean northernGrowing = month >= 4 && month <= 9;
    boolean growing = hemisphere == Hemisphere.SOUTHERN ? !northernGrowing : northernGrowing;
    return growing ? CareSeason.GROWING : CareSeason.RESTING;
  }

  private static String label(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
