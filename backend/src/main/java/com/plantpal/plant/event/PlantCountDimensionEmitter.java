package com.plantpal.plant.event;

import com.plantpal.plant.config.PlantKafkaTopicConfig;
import io.platform.contracts.events.DimensionEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emits the platform {@code dimension.event} (contracts {@link DimensionEvent}) to Kafka strictly
 * AFTER the plant-mutating transaction commits. If the transaction rolls back, the listener never
 * fires and Treasury never sees a phantom {@code plant_count} delta (FIX-12).
 */
@Component
public class PlantCountDimensionEmitter {

  private static final Logger log = LoggerFactory.getLogger(PlantCountDimensionEmitter.class);

  private static final String APP_ID = "plantpal";
  private static final String PLANT_COUNT_DIMENSION = "plant_count";

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public PlantCountDimensionEmitter(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlantCountChanged(PlantCountChangedEvent event) {
    DimensionEvent dimensionEvent =
        new DimensionEvent(
            UUID.randomUUID(),
            APP_ID,
            String.valueOf(event.getUserId()),
            PLANT_COUNT_DIMENSION,
            event.getDelta(),
            OffsetDateTime.now(ZoneOffset.UTC));
    kafkaTemplate.send(PlantKafkaTopicConfig.DIMENSION_EVENT_TOPIC, dimensionEvent);
    log.debug(
        "Emitted plant_count dimension event: userId={}, delta={}",
        event.getUserId(),
        event.getDelta());
  }
}
