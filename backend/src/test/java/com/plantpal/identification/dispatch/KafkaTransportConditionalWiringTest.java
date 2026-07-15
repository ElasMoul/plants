package com.plantpal.identification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.consumer.IdentificationConsumer;
import com.plantpal.plant.config.PlantKafkaTopicConfig;
import com.plantpal.shared.config.KafkaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * T-DEPLOY.5: every bean that would otherwise dial a Kafka broker at startup (or would produce to
 * one) is gated by a {@code @ConditionalOnProperty(prefix = "app.identification", name =
 * "transport", ...)} annotation. A full {@code @SpringBootTest} that actually flips {@code
 * app.identification.transport} and asserts which beans load would be the strongest check, but this
 * repo's Spring context requires Testcontainers/Docker (see {@code AbstractIntegrationTest}), which
 * isn't available to run locally right now — see PROGRESS.md. This reflection-only test is the
 * cheap substitute: it can't catch a Spring wiring mistake, but it does catch the likelier failure
 * mode of a typo'd property name/value on one of these annotations silently breaking the
 * kafka/in-process split.
 */
@DisplayName("Kafka transport conditional-bean wiring — reflection checks")
class KafkaTransportConditionalWiringTest {

  private static final String PREFIX = "app.identification";
  private static final String NAME = "transport";

  private static ConditionalOnProperty conditionOn(Class<?> type) {
    ConditionalOnProperty annotation = type.getAnnotation(ConditionalOnProperty.class);
    assertThat(annotation)
        .as("%s must carry @ConditionalOnProperty", type.getSimpleName())
        .isNotNull();
    return annotation;
  }

  @Test
  @DisplayName("KafkaConfig requires transport=kafka, defaulting on when the property is unset")
  void kafkaConfigIsGatedOnKafkaTransport() {
    ConditionalOnProperty annotation = conditionOn(KafkaConfig.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("kafka");
    assertThat(annotation.matchIfMissing()).isTrue();
  }

  @Test
  @DisplayName("identification KafkaTopicConfig requires transport=kafka, defaulting on when unset")
  void identificationKafkaTopicConfigIsGatedOnKafkaTransport() {
    ConditionalOnProperty annotation = conditionOn(KafkaTopicConfig.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("kafka");
    assertThat(annotation.matchIfMissing()).isTrue();
  }

  @Test
  @DisplayName("PlantKafkaTopicConfig requires transport=kafka, defaulting on when unset")
  void plantKafkaTopicConfigIsGatedOnKafkaTransport() {
    ConditionalOnProperty annotation = conditionOn(PlantKafkaTopicConfig.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("kafka");
    assertThat(annotation.matchIfMissing()).isTrue();
  }

  @Test
  @DisplayName("IdentificationConsumer requires transport=kafka, defaulting on when unset")
  void identificationConsumerIsGatedOnKafkaTransport() {
    ConditionalOnProperty annotation = conditionOn(IdentificationConsumer.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("kafka");
    assertThat(annotation.matchIfMissing()).isTrue();
  }

  @Test
  @DisplayName("KafkaIdentificationDispatcher requires transport=kafka, defaulting on when unset")
  void kafkaDispatcherIsGatedOnKafkaTransport() {
    ConditionalOnProperty annotation = conditionOn(KafkaIdentificationDispatcher.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("kafka");
    assertThat(annotation.matchIfMissing()).isTrue();
  }

  @Test
  @DisplayName(
      "InProcessIdentificationDispatcher requires transport=in-process, NOT active by default"
          + " (matchIfMissing must stay false so a misconfigured prod boot doesn't silently"
          + " combine both dispatchers)")
  void inProcessDispatcherIsGatedOnInProcessTransportOnly() {
    ConditionalOnProperty annotation = conditionOn(InProcessIdentificationDispatcher.class);
    assertThat(annotation.prefix()).isEqualTo(PREFIX);
    assertThat(annotation.name()).containsExactly(NAME);
    assertThat(annotation.havingValue()).isEqualTo("in-process");
    assertThat(annotation.matchIfMissing()).isFalse();
  }

  @Test
  @DisplayName("the two dispatcher conditions are mutually exclusive havingValues")
  void dispatcherConditionsAreMutuallyExclusive() {
    ConditionalOnProperty kafka = conditionOn(KafkaIdentificationDispatcher.class);
    ConditionalOnProperty inProcess = conditionOn(InProcessIdentificationDispatcher.class);
    assertThat(kafka.havingValue()).isNotEqualTo(inProcess.havingValue());
  }
}
