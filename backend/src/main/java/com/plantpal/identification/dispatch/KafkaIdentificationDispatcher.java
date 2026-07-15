package com.plantpal.identification.dispatch;

import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Default dispatch strategy — publishes to the {@code identification.requested} Kafka topic,
 * exactly as {@code IdentificationServiceImpl} did before the dispatch abstraction was introduced.
 * {@code IdentificationConsumer} (a {@code @KafkaListener}) picks the event back up and calls
 * {@code processIdentification(...)}.
 *
 * <p>Active only when {@code app.identification.transport=kafka} (the default) — see {@link
 * com.plantpal.shared.config.KafkaConfig}, whose {@code KafkaTemplate} bean shares the same
 * condition, so this bean and its dependency always appear together.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "kafka",
    matchIfMissing = true)
public class KafkaIdentificationDispatcher implements IdentificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(KafkaIdentificationDispatcher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public KafkaIdentificationDispatcher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void dispatch(IdentificationRequestedEvent event) {
    kafkaTemplate.send(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC, event);
    log.info(
        "Published IdentificationRequestedEvent via Kafka: id={}", event.getIdentificationId());
  }
}
