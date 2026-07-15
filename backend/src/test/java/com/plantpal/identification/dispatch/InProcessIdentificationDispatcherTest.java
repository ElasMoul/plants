package com.plantpal.identification.dispatch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.service.IdentificationService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("InProcessIdentificationDispatcher — Unit Tests")
class InProcessIdentificationDispatcherTest {

  @Mock private IdentificationService identificationService;

  @InjectMocks private InProcessIdentificationDispatcher dispatcher;

  @Nested
  @DisplayName("dispatch()")
  class Dispatch {

    @Test
    @DisplayName(
        "should invoke IdentificationService.processIdentification directly (the same entry"
            + " point IdentificationConsumer calls), crossing the bean boundary so the method's"
            + " own @Async(\"aiTaskExecutor\") applies — never Kafka, never a self-invocation")
    void shouldInvokeProcessIdentificationEntryPoint() {
      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(42L)
              .userId(7L)
              .photoUrl("/photos/uuid.jpg")
              .aiModelPreference("GITHUB_GPT4O")
              .requestedAt(Instant.now())
              .build();

      dispatcher.dispatch(event);

      verify(identificationService).processIdentification(eq(event));
      verifyNoMoreInteractions(identificationService);
    }

    @Test
    @DisplayName("should pass the exact event instance through unmodified")
    void shouldPassEventThroughUnmodified() {
      IdentificationRequestedEvent event =
          IdentificationRequestedEvent.builder()
              .identificationId(1L)
              .userId(1L)
              .photoUrl("/photos/a.jpg")
              .aiModelPreference("DEEPSEEK_R1")
              .organs(java.util.List.of("leaf"))
              .userContext("why is this yellow?")
              .requestedAt(Instant.now())
              .build();

      dispatcher.dispatch(event);

      verify(identificationService).processIdentification(event);
    }
  }
}
