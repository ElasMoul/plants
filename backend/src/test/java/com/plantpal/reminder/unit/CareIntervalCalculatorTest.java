package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.plant.entity.Placement;
import com.plantpal.reminder.CareIntervalCalculator;
import com.plantpal.reminder.CareIntervalCalculator.CareInterval;
import com.plantpal.reminder.entity.CareSeason;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Hemisphere;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CareIntervalCalculator — Unit Tests")
class CareIntervalCalculatorTest {

  private static final LocalDate WINTER = LocalDate.of(2026, 1, 15);
  private static final LocalDate SUMMER = LocalDate.of(2026, 7, 15);

  private static CareInterval watering(
      LocalDate date, Hemisphere hemisphere, Placement placement, Integer override) {
    return CareIntervalCalculator.calculate(
        CareType.WATERING, 7, date, hemisphere, placement, override);
  }

  @Test
  @DisplayName("should differ between growing and resting seasons")
  void shouldDifferBySeason() {
    assertThat(watering(SUMMER, Hemisphere.NORTHERN, Placement.INDOOR, null).days()).isEqualTo(7);
    assertThat(watering(WINTER, Hemisphere.NORTHERN, Placement.INDOOR, null).days()).isEqualTo(11);
  }

  @Test
  @DisplayName("should swap seasons in the southern hemisphere")
  void shouldSwapSeasonsInSouth() {
    assertThat(watering(WINTER, Hemisphere.SOUTHERN, Placement.INDOOR, null).season())
        .isEqualTo(CareSeason.GROWING);
    assertThat(watering(WINTER, Hemisphere.NORTHERN, Placement.INDOOR, null).season())
        .isEqualTo(CareSeason.RESTING);
    assertThat(watering(SUMMER, Hemisphere.SOUTHERN, Placement.INDOOR, null).days()).isEqualTo(11);
  }

  @Test
  @DisplayName("should default to northern and say so when no hemisphere is known")
  void shouldDefaultToNorthern() {
    CareInterval result = watering(WINTER, null, Placement.INDOOR, null);

    assertThat(result.season()).isEqualTo(CareSeason.RESTING);
    assertThat(result.reason()).contains("northern hemisphere assumed");
  }

  @Test
  @DisplayName("should shorten outdoor watering and treat greenhouse as indoor")
  void shouldDifferByPlacement() {
    assertThat(watering(SUMMER, Hemisphere.NORTHERN, Placement.OUTDOOR, null).days()).isEqualTo(5);
    assertThat(watering(WINTER, Hemisphere.NORTHERN, Placement.OUTDOOR, null).days()).isEqualTo(8);
    assertThat(watering(WINTER, Hemisphere.NORTHERN, Placement.GREENHOUSE, null).days())
        .isEqualTo(11);
    assertThat(watering(SUMMER, Hemisphere.NORTHERN, Placement.GREENHOUSE, null).days())
        .isEqualTo(7);
  }

  @Test
  @DisplayName("should double fertilizing in the resting season and ignore placement")
  void shouldAdjustFertilizing() {
    CareInterval winter =
        CareIntervalCalculator.calculate(
            CareType.FERTILIZING, 14, WINTER, Hemisphere.NORTHERN, Placement.OUTDOOR, null);

    assertThat(winter.days()).isEqualTo(28);
  }

  @Test
  @DisplayName("should name season and placement in the reason")
  void shouldExplainAdjustment() {
    CareInterval result = watering(WINTER, Hemisphere.NORTHERN, Placement.OUTDOOR, null);

    assertThat(result.reason()).contains("Resting season", "outdoor", "7", "8");
  }

  @Test
  @DisplayName("should leave other care types unchanged and unexplained")
  void shouldNotAdjustOtherCareTypes() {
    CareInterval result =
        CareIntervalCalculator.calculate(
            CareType.PRUNING, 30, WINTER, Hemisphere.NORTHERN, Placement.OUTDOOR, null);

    assertThat(result.days()).isEqualTo(30);
    assertThat(result.reason()).isNull();
    assertThat(result.season()).isNull();
  }

  @Test
  @DisplayName("should let an override beat the calculation")
  void shouldPreferOverride() {
    CareInterval result = watering(WINTER, Hemisphere.NORTHERN, Placement.OUTDOOR, 3);

    assertThat(result.days()).isEqualTo(3);
    assertThat(result.reason()).contains("User-set");
  }

  @Test
  @DisplayName("should resolve hemisphere from the country code")
  void shouldResolveHemisphere() {
    assertThat(Hemisphere.fromCountry("au")).isEqualTo(Hemisphere.SOUTHERN);
    assertThat(Hemisphere.fromCountry("FR")).isEqualTo(Hemisphere.NORTHERN);
    assertThat(Hemisphere.fromCountry(null)).isNull();
  }
}
