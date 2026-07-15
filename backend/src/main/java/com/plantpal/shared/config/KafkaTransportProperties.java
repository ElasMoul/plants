package com.plantpal.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound from {@code app.identification.transport} (T-DEPLOY.5). Despite the identification-scoped
 * property name, this is the single "is a Kafka broker present in this environment" toggle for the
 * whole app — every Kafka producer in PlantPal (the identification-requested dispatch in {@code
 * com.plantpal.identification.dispatch}, the {@code identification.completed} notification in
 * {@code IdentificationServiceImpl}, and the {@code plant_count} dimension event in {@link
 * com.plantpal.plant.event.PlantCountDimensionEmitter}) checks {@link #isKafkaEnabled()} before
 * calling {@code KafkaTemplate.send()}. A plain "is the bean present" check isn't sufficient to
 * gate this: Spring Boot's own Kafka autoconfiguration still supplies a fallback {@code
 * KafkaTemplate} bean when our conditional one (see {@link KafkaConfig}) is absent, and a
 * producer's first send against an unreachable broker can block the calling thread for up to {@code
 * max.block.ms} (default 60s) while it waits for cluster metadata.
 *
 * <p>{@code kafka} (default — dev/local/test) keeps the existing async Kafka pipeline. {@code
 * in-process} (prod/staging, per the v1.0.0 ships-without-Kafka decision) runs identification
 * synchronously in-process via {@code aiTaskExecutor} and skips every other Kafka producer.
 */
@ConfigurationProperties(prefix = "app.identification")
public record KafkaTransportProperties(@DefaultValue("kafka") String transport) {

  public static final String KAFKA = "kafka";
  public static final String IN_PROCESS = "in-process";

  public boolean isKafkaEnabled() {
    return KAFKA.equalsIgnoreCase(transport);
  }
}
