package com.plantpal.identification.client;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class DeepSeekDirectClientTest {
  private HttpServer server;
  private DeepSeekDirectClient client;
  private final AtomicReference<String> body = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private int status = 200;
  private String response =
      "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Plant advice\"}]}";

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/anthropic/v1/messages",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    client =
        new DeepSeekDirectClient(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/anthropic",
            "test-key",
            "deepseek-flash");
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void sendsNativeVisionRequest() throws Exception {
    assertThat(client.identifyPlant(new byte[] {1, 2}, "image/png", "yellow leaves"))
        .isEqualTo("Plant advice");
    var json = new ObjectMapper().readTree(body.get());
    assertThat(authorization.get()).isEqualTo("test-key");
    assertThat(json.path("model").asText()).isEqualTo("deepseek-flash");
    assertThat(
            json.path("messages")
                .path(0)
                .path("content")
                .path(1)
                .path("source")
                .path("data")
                .asText())
        .isEqualTo("AQI=");
    assertThat(json.path("thinking").path("type").asText()).isEqualTo("disabled");
  }

  @Test
  void missingKeyFailsBeforeNetwork() {
    var unavailable = new DeepSeekDirectClient("http://127.0.0.1:1", "", "deepseek-flash");
    assertThat(unavailable.isAvailable()).isFalse();
    assertThatThrownBy(() -> unavailable.chat("system", "hello"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("not configured");
  }

  @Test
  void rateLimitIsActionable() {
    status = 429;
    response = "{}";
    assertThatThrownBy(() -> client.chat("system", "hello")).isInstanceOf(RateLimitException.class);
  }

  @Test
  void truncatedOrEmptyResponsesAreRejected() {
    response =
        "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"partial\"}]}";
    assertThatThrownBy(() -> client.chat("system", "hello"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("incomplete");
    response = "{\"stop_reason\":\"end_turn\",\"content\":[]}";
    assertThatThrownBy(() -> client.chat("system", "hello"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("empty");
  }
}
