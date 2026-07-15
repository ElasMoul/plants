package com.plantpal.plant.event;

import com.plantpal.plant.config.PlantKafkaTopicConfig;
import com.plantpal.shared.config.KafkaTransportProperties;
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
 *
 * <p>T-DEPLOY.5: this is a fire-and-forget platform-analytics signal with no in-repo consumer, so
 * when {@code app.identification.transport=in-process} (no Kafka broker expected in that
 * environment — see {@link KafkaTransportProperties}) the send is skipped entirely rather than
 * risking a blocking metadata fetch against an absent broker on the post-commit thread.
 */
@Component
public class PlantCountDimensionEmitter {

  private static final Logger log = LoggerFactory.getLogger(PlantCountDimensionEmitter.class);

  private static final String APP_ID = "plantpal";
  private static final String PLANT_COUNT_DIMENSION = "plant_count";

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaTransportProperties kafkaTransportProperties;

  public PlantCountDimensionEmitter(
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaTransportProperties kafkaTransportProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaTransportProperties = kafkaTransportProperties;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlantCountChanged(PlantCountChangedEvent event) {
    if (!kafkaTransportProperties.isKafkaEnabled()) {
      log.debug(
          "Skipping plant_count dimension event (transport=in-process): userId={}, delta={}",
          event.getUserId(),
          event.getDelta());
      return;
    }
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
