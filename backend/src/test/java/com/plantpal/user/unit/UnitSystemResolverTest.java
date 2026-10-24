package com.plantpal.user.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.user.entity.UnitSystem;
import com.plantpal.user.service.UnitSystemResolver;
import com.plantpal.user.service.UserZones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("UnitSystemResolver / UserZones - Unit Tests")
class UnitSystemResolverTest {

  @Test
  @DisplayName("stored choice wins over the region")
  void storedWins() {
    assertThat(UnitSystemResolver.resolve(UnitSystem.METRIC, "US")).isEqualTo(UnitSystem.METRIC);
    assertThat(UnitSystemResolver.resolve(UnitSystem.IMPERIAL, "FR"))
        .isEqualTo(UnitSystem.IMPERIAL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"US", "us", "LR", "MM"})
  @DisplayName("no choice and an imperial-region country gives imperial")
  void regionImperial(String country) {
    assertThat(UnitSystemResolver.resolve(null, country)).isEqualTo(UnitSystem.IMPERIAL);
  }

  @Test
  @DisplayName("no choice and no or metric region gives metric")
  void defaultMetric() {
    assertThat(UnitSystemResolver.resolve(null, null)).isEqualTo(UnitSystem.METRIC);
    assertThat(UnitSystemResolver.resolve(null, "FR")).isEqualTo(UnitSystem.METRIC);
  }

  @Test
  @DisplayName("null or invalid stored zone means UTC")
  void zones() {
    assertThat(UserZones.of(null).getId()).isEqualTo("Z");
    assertThat(UserZones.of("nope/zone").getId()).isEqualTo("Z");
    assertThat(UserZones.of("Europe/Berlin").getId()).isEqualTo("Europe/Berlin");
  }
}
