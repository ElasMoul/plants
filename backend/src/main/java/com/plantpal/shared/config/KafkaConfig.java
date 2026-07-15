package com.plantpal.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Only active when {@code app.identification.transport=kafka} (the default — see {@link
 * KafkaTransportProperties}). In {@code in-process} mode (prod/staging, T-DEPLOY.5) this bean is
 * absent — Spring Boot's own Kafka autoconfiguration still supplies a fallback {@code
 * KafkaTemplate} bean for anything that unconditionally injects one, but nothing actually calls
 * {@code send()} on it in that mode (see {@link KafkaTransportProperties#isKafkaEnabled()} guards
 * at every call site). The topic and consumer beans that would otherwise dial the broker at startup
 * are gated the same way — see {@link com.plantpal.identification.config.KafkaTopicConfig}, {@link
 * com.plantpal.plant.config.PlantKafkaTopicConfig}, and {@link
 * com.plantpal.identification.consumer.IdentificationConsumer}.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "kafka",
    matchIfMissing = true)
public class KafkaConfig {

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(
      ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
