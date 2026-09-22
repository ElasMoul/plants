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
      "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Plant advice\"}}]}";

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    client =
        new DeepSeekDirectClient(
            "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "deepseek-flash");
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
    assertThat(authorization.get()).isEqualTo("Bearer test-key");
    assertThat(json.path("model").asText()).isEqualTo("deepseek-flash");
    assertThat(
            json.path("messages")
                .path(1)
                .path("content")
                .path(1)
                .path("image_url")
                .path("url")
                .asText())
        .isEqualTo("data:image/png;base64,AQI=");
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
        "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}";
    assertThatThrownBy(() -> client.chat("system", "hello"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("incomplete");
    response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}";
    assertThatThrownBy(() -> client.chat("system", "hello"))
        .isInstanceOf(PlantPalException.class)
        .hasMessageContaining("empty");
  }
}
