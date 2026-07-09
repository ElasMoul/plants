package com.plantpal.statefeed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.event.IdentificationCompletedEvent;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link StateFeedEmitter} over real HTTP against a loopback stub server (same technique
 * as {@code com.plantpal.gateway.GatewayClientTest}) — verifies actual payload shape, the
 * enabled/disabled gate, and that any transport failure is swallowed rather than propagated (the
 * state-feed is a read-only mirror, spec-state-feed.md §3: PlantPal must never care if it's down).
 * Uses a synchronous (same-thread) {@link Executor} instead of the real {@code aiTaskExecutor} so
 * assertions don't need to poll/await a background thread.
 */
class StateFeedEmitterTest {

  private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void onApplicationReady_disabled_sendsNothing() {
    List<String> receivedBodies = new CopyOnWriteArrayList<>();
    startStubServer(200, receivedBodies);
    StateFeedEmitter emitter =
        new StateFeedEmitter(new StateFeedProperties(false, baseUrl()), SAME_THREAD_EXECUTOR);

    emitter.onApplicationReady();

    assertThat(receivedBodies).isEmpty();
  }

  @Test
  void onApplicationReady_enabled_postsAppStatusRunningPayload() throws IOException {
    List<String> receivedBodies = new CopyOnWriteArrayList<>();
    startStubServer(200, receivedBodies);
    StateFeedEmitter emitter =
        new StateFeedEmitter(new StateFeedProperties(true, baseUrl()), SAME_THREAD_EXECUTOR);

    emitter.onApplicationReady();

    assertThat(receivedBodies).hasSize(1);
    JsonNode body = objectMapper.readTree(receivedBodies.get(0));
    assertThat(body.get("type").asText()).isEqualTo("app.status");
    assertThat(body.get("payload").get("appId").asText()).isEqualTo("plantpal");
    assertThat(body.get("payload").get("status").asText()).isEqualTo("running");
    assertThat(body.get("timestamp").asText()).isNotBlank();
  }

  @Test
  void onIdentificationCompleted_disabled_sendsNothing() {
    List<String> receivedBodies = new CopyOnWriteArrayList<>();
    startStubServer(200, receivedBodies);
    StateFeedEmitter emitter =
        new StateFeedEmitter(new StateFeedProperties(false, baseUrl()), SAME_THREAD_EXECUTOR);

    emitter.onIdentificationCompleted(
        IdentificationCompletedEvent.builder()
            .identificationId(1L)
            .status("COMPLETED")
            .completedAt(Instant.now())
            .build());

    assertThat(receivedBodies).isEmpty();
  }

  @Test
  void onIdentificationCompleted_enabled_postsActivityCountPayload() throws IOException {
    List<String> receivedBodies = new CopyOnWriteArrayList<>();
    startStubServer(200, receivedBodies);
    StateFeedEmitter emitter =
        new StateFeedEmitter(new StateFeedProperties(true, baseUrl()), SAME_THREAD_EXECUTOR);

    emitter.onIdentificationCompleted(
        IdentificationCompletedEvent.builder()
            .identificationId(42L)
            .status("COMPLETED")
            .completedAt(Instant.now())
            .build());

    assertThat(receivedBodies).hasSize(1);
    JsonNode body = objectMapper.readTree(receivedBodies.get(0));
    assertThat(body.get("type").asText()).isEqualTo("activity.count");
    assertThat(body.get("payload").get("componentId").asText()).isEqualTo("plantpal");
    assertThat(body.get("payload").get("activity").asText()).isEqualTo("identification.completed");
    assertThat(body.get("payload").get("count").asInt()).isEqualTo(1);
  }

  @Test
  void emit_serverError_isSwallowedNotThrown() {
    List<String> receivedBodies = new CopyOnWriteArrayList<>();
    startStubServer(503, receivedBodies);
    StateFeedEmitter emitter =
        new StateFeedEmitter(new StateFeedProperties(true, baseUrl()), SAME_THREAD_EXECUTOR);

    // Must not throw — the feed being down is never PlantPal's problem.
    emitter.onApplicationReady();

    assertThat(receivedBodies).hasSize(1); // request was made, response was just an error
  }

  @Test
  void emit_unreachableHost_isSwallowedNotThrown() throws IOException {
    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    }
    // Socket closed on return above — nothing listening, connection refused.
    StateFeedEmitter emitter =
        new StateFeedEmitter(
            new StateFeedProperties(true, "http://localhost:" + deadPort), SAME_THREAD_EXECUTOR);

    // Must not throw.
    emitter.onApplicationReady();
  }

  @Test
  void applicationReadyEvent_listenerAnnotationPresent() throws NoSuchMethodException {
    var method = StateFeedEmitter.class.getMethod("onApplicationReady");
    assertThat(method.isAnnotationPresent(org.springframework.context.event.EventListener.class))
        .isTrue();
  }

  private void startStubServer(int status, List<String> receivedBodies) {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/events",
          exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            receivedBodies.add(new String(requestBytes));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
          });
      server.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }
}
