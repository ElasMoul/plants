package com.plantpal.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KafkaTransportProperties — Unit Tests")
class KafkaTransportPropertiesTest {

  @Nested
  @DisplayName("isKafkaEnabled()")
  class IsKafkaEnabled {

    @Test
    @DisplayName("should be true for 'kafka'")
    void trueForKafka() {
      assertThat(new KafkaTransportProperties("kafka").isKafkaEnabled()).isTrue();
    }

    @Test
    @DisplayName("should be case-insensitive")
    void caseInsensitive() {
      assertThat(new KafkaTransportProperties("KAFKA").isKafkaEnabled()).isTrue();
    }

    @Test
    @DisplayName("should be false for 'in-process'")
    void falseForInProcess() {
      assertThat(new KafkaTransportProperties("in-process").isKafkaEnabled()).isFalse();
    }

    @Test
    @DisplayName("should be false for an unrecognized value rather than fail-open")
    void falseForUnrecognizedValue() {
      assertThat(new KafkaTransportProperties("bogus").isKafkaEnabled()).isFalse();
    }
  }
}
