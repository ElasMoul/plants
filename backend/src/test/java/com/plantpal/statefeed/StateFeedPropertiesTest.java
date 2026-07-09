package com.plantpal.statefeed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StateFeedPropertiesTest {

  @Test
  void explicitConstruction_holdsGivenValues() {
    StateFeedProperties properties = new StateFeedProperties(true, "http://state-feed:9000");

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.url()).isEqualTo("http://state-feed:9000");
  }
}
