package com.plantpal.identification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Only active when {@code app.identification.transport=kafka} (the default). In {@code in-process}
 * mode (T-DEPLOY.5) these {@link NewTopic} beans are absent, so {@code KafkaAdmin} never attempts
 * to auto-create them against a broker that may not exist in that environment.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "kafka",
    matchIfMissing = true)
public class KafkaTopicConfig {

  public static final String IDENTIFICATION_REQUESTED_TOPIC = "identification.requested";
  public static final String IDENTIFICATION_COMPLETED_TOPIC = "identification.completed";

  @Bean
  public NewTopic identificationRequestedTopic() {
    return TopicBuilder.name(IDENTIFICATION_REQUESTED_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic identificationCompletedTopic() {
    return TopicBuilder.name(IDENTIFICATION_COMPLETED_TOPIC).partitions(3).replicas(1).build();
  }
}
