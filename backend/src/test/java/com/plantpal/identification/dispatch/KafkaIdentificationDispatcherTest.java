package com.plantpal.identification.dispatch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.plantpal.identification.config.KafkaTopicConfig;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaIdentificationDispatcher — Unit Tests")
class KafkaIdentificationDispatcherTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private KafkaIdentificationDispatcher dispatcher;

  @Nested
  @DisplayName("dispatch()")
  class Dispatch {

    @Test
    @DisplayName("should publish the event to the identification.requested topic")
    void shouldPublishToRequestedTopic() {
      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(2L)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("GITHUB_GPT4O")
              .requestedAt(Instant.now())
              .build();

      dispatcher.dispatch(event);

      verify(kafkaTemplate).send(eq(KafkaTopicConfig.IDENTIFICATION_REQUESTED_TOPIC), eq(event));
    }
  }
}
