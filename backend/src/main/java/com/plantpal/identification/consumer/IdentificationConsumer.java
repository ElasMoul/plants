package com.plantpal.identification.consumer;

import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.service.IdentificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
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
