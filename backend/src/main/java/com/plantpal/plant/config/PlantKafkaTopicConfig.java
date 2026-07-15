package com.plantpal.plant.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Only active when {@code app.identification.transport=kafka} (the default). In {@code in-process}
 * mode (T-DEPLOY.5) this {@link NewTopic} bean is absent, so {@code KafkaAdmin} never attempts to
 * auto-create it against a broker that may not exist in that environment. See {@link
 * com.plantpal.plant.event.PlantCountDimensionEmitter} for the corresponding producer-side guard.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "kafka",
    matchIfMissing = true)
public class PlantKafkaTopicConfig {

  public static final String DIMENSION_EVENT_TOPIC = "dimension.events";

  @Bean
  public NewTopic dimensionEventTopic() {
    return TopicBuilder.name(DIMENSION_EVENT_TOPIC).partitions(3).replicas(1).build();
  }
}
