package com.plantpal.plant.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plantpal.plant.config.PlantKafkaTopicConfig;
import com.plantpal.plant.event.PlantCountChangedEvent;
import com.plantpal.plant.event.PlantCountDimensionEmitter;
import com.plantpal.shared.config.KafkaTransportProperties;
import io.platform.contracts.events.DimensionEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantCountDimensionEmitter — Unit Tests")
class PlantCountDimensionEmitterTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private PlantCountDimensionEmitter emitter;

  @BeforeEach
  void setUp() {
    emitter =
        new PlantCountDimensionEmitter(
            kafkaTemplate, new KafkaTransportProperties(KafkaTransportProperties.KAFKA));
  }

  @Test
  @DisplayName("should forward a +1 plant_count delta to Kafka as a contracts DimensionEvent")
  void shouldForwardPositiveDelta() {
    emitter.onPlantCountChanged(new PlantCountChangedEvent(1L, 1));

    ArgumentCaptor<DimensionEvent> captor = ArgumentCaptor.forClass(DimensionEvent.class);
    verify(kafkaTemplate).send(eq(PlantKafkaTopicConfig.DIMENSION_EVENT_TOPIC), captor.capture());
    DimensionEvent event = captor.getValue();
    assertThat(event.getEventId()).isNotNull();
    assertThat(event.getAppId()).isEqualTo("plantpal");
    assertThat(event.getUserId()).isEqualTo("1");
    assertThat(event.getDimensionKey()).isEqualTo("plant_count");
    assertThat(event.getDelta()).isEqualTo(1);
    assertThat(event.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("should forward a -1 plant_count delta to Kafka as a contracts DimensionEvent")
  void shouldForwardNegativeDelta() {
    emitter.onPlantCountChanged(new PlantCountChangedEvent(42L, -1));

    ArgumentCaptor<DimensionEvent> captor = ArgumentCaptor.forClass(DimensionEvent.class);
    verify(kafkaTemplate).send(eq(PlantKafkaTopicConfig.DIMENSION_EVENT_TOPIC), captor.capture());
    DimensionEvent event = captor.getValue();
    assertThat(event.getUserId()).isEqualTo("42");
    assertThat(event.getDelta()).isEqualTo(-1);
  }

  @Test
  @DisplayName(
      "should skip the Kafka send entirely when transport=in-process (T-DEPLOY.5, no broker"
          + " expected)")
  void shouldSkipSendWhenTransportIsInProcess() {
    PlantCountDimensionEmitter inProcessEmitter =
        new PlantCountDimensionEmitter(
            kafkaTemplate, new KafkaTransportProperties(KafkaTransportProperties.IN_PROCESS));

    inProcessEmitter.onPlantCountChanged(new PlantCountChangedEvent(1L, 1));

    verify(kafkaTemplate, never()).send(any(), any());
  }

  @Test
  @DisplayName("listener must run strictly AFTER_COMMIT so a rollback never emits (FIX-12)")
  void listenerIsBoundToAfterCommitPhase() throws NoSuchMethodException {
    Method listener =
        PlantCountDimensionEmitter.class.getMethod(
            "onPlantCountChanged", PlantCountChangedEvent.class);
    TransactionalEventListener annotation =
        listener.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    // Default fallbackExecution=false: outside a transaction nothing is emitted either.
    assertThat(annotation.fallbackExecution()).isFalse();
  }
}
