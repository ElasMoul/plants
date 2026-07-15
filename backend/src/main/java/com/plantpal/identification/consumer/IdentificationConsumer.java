package com.plantpal.identification.consumer;

import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.service.IdentificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Only active when {@code app.identification.transport=kafka} (the default). In {@code in-process}
 * mode (T-DEPLOY.5) this bean is absent — {@code
 * com.plantpal.identification.dispatch.InProcessIdentificationDispatcher} invokes {@link
 * IdentificationService#processIdentification(IdentificationRequestedEvent)} directly instead, so
 * no listener container ever starts (and retries connecting to a broker that may not exist in that
 * environment).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "kafka",
    matchIfMissing = true)
public class IdentificationConsumer {

  private static final Logger log = LoggerFactory.getLogger(IdentificationConsumer.class);

  private final IdentificationService identificationService;

  public IdentificationConsumer(IdentificationService identificationService) {
    this.identificationService = identificationService;
  }

  @KafkaListener(
      topics = "identification.requested",
      groupId = "plantpal-identification",
      containerFactory = "kafkaListenerContainerFactory")
  public void onIdentificationRequested(IdentificationRequestedEvent event) {
    log.info("Processing identification event: id={}", event.getIdentificationId());
    identificationService.processIdentification(event);
  }
}
