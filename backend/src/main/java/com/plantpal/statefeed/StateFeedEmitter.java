package com.plantpal.statefeed;

import io.platform.contracts.events.ActivityCountEvent;
import io.platform.contracts.events.ActivityCountPayload;
import io.platform.contracts.events.AppStatusEvent;
import io.platform.contracts.events.AppStatusPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fire-and-forget outbound port to the platform state-feed (D029, {@code state.event} contract).
 * The feed is a **read-only mirror** (spec-state-feed.md §3) — if it is down, slow, or absent,
 * PlantPal must never notice: every emit runs off the request thread and swallows any failure at
 * WARN, never propagating into a caller. Modeled on {@code com.plantpal.gateway.GatewayClient} /
 * {@code PlantCountDimensionEmitter} — same profile-gated, D009-safe shape.
 *
 * <p>Emits exactly two event types, deliberately scoped to this chunk:
 *
 * <ul>
 *   <li>{@code app.status} once, on {@link ApplicationReadyEvent} — "plantpal is up."
 *   <li>{@code activity.count} each time an identification finishes (listens to the existing {@code
 *       IdentificationCompletedEvent} Spring application event — no new cross-package injection;
 *       identification already publishes this event for {@link
 *       com.plantpal.identification.event.DuplicateCareCardRemovedEvent}'s sibling wiring).
 * </ul>
 */
@Component
public class StateFeedEmitter {

  private static final Logger log = LoggerFactory.getLogger(StateFeedEmitter.class);

  private static final String APP_ID = "plantpal";
  private static final String IDENTIFICATION_COMPLETED_ACTIVITY = "identification.completed";
  private static final String EVENTS_PATH = "/events";
  private static final int CONNECT_TIMEOUT_MS = 2_000;
  private static final int READ_TIMEOUT_MS = 5_000;

  private final StateFeedProperties properties;
  private final RestClient restClient;
  private final Executor executor;

  public StateFeedEmitter(
      StateFeedProperties properties, @Qualifier("aiTaskExecutor") Executor executor) {
    this.properties = properties;
    this.executor = executor;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    requestFactory.setReadTimeout(READ_TIMEOUT_MS);
    this.restClient =
        RestClient.builder().baseUrl(properties.url()).requestFactory(requestFactory).build();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (!properties.enabled()) {
      return;
    }
    AppStatusPayload payload =
        new AppStatusPayload().appId(APP_ID).status(AppStatusPayload.StatusEnum.RUNNING);
    AppStatusEvent event =
        new AppStatusEvent()
            .type(AppStatusEvent.TypeEnum.APP_STATUS)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .payload(payload);
    emitAsync(event);
  }

  @EventListener
  public void onIdentificationCompleted(
      com.plantpal.identification.event.IdentificationCompletedEvent event) {
    if (!properties.enabled()) {
      return;
    }
    ActivityCountPayload payload =
        new ActivityCountPayload()
            .componentId(APP_ID)
            .activity(IDENTIFICATION_COMPLETED_ACTIVITY)
            .count(1);
    ActivityCountEvent activityCountEvent =
        new ActivityCountEvent()
            .type(ActivityCountEvent.TypeEnum.ACTIVITY_COUNT)
            .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
            .payload(payload);
    emitAsync(activityCountEvent);
  }

  private void emitAsync(Object event) {
    CompletableFuture.runAsync(() -> emit(event), executor);
  }

  private void emit(Object event) {
    try {
      restClient
          .post()
          .uri(EVENTS_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(event)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      // Read-only mirror (spec-state-feed.md §3): never let a down/slow state-feed affect
      // PlantPal. Log and move on — no retry, no propagation.
      log.warn("state-feed emit failed, ignoring [event={}]: {}", event, e.getMessage());
    }
  }
}
