package com.plantpal.plant.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PlantKafkaTopicConfig {

  public static final String DIMENSION_EVENT_TOPIC = "dimension.events";

  @Bean
  public NewTopic dimensionEventTopic() {
    return TopicBuilder.name(DIMENSION_EVENT_TOPIC).partitions(3).replicas(1).build();
  }
}
