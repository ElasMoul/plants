package com.plantpal.identification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AnthropicClient - Unit Tests")
class AnthropicClientTest {

  private static final String MODEL = "claude-test";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockWebServer server;
  private AnthropicClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client = new AnthropicClient(baseUrl(), "test-key", MODEL);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Nested
  @DisplayName("request shape")
  class RequestShape {

    @Test
    @DisplayName("identifyPlant sends the image as base64 plus headers, model and token cap")
    void identifyPlantSendsImage() throws Exception {
      server.enqueue(text("{\"species\":\"Ficus\"}"));

      String result = client.identifyPlant(new byte[] {1, 2}, "image/png");

      assertThat(result).isEqualTo("{\"species\":\"Ficus\"}");
      RecordedRequest request = server.takeRequest();
      assertThat(request.getPath()).isEqualTo("/v1/messages");
      assertThat(request.getHeader("x-api-key")).isEqualTo("test-key");
      assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
      JsonNode json = body(request);
      assertThat(json.path("model").asText()).isEqualTo(MODEL);
      assertThat(json.path("max_tokens").asInt()).isEqualTo(4096);
      assertThat(json.path("system").asText())
          .isEqualTo(GitHubModelsClient.PLANT_IDENTIFICATION_SYSTEM_PROMPT);
      JsonNode content = json.path("messages").path(0).path("content");
      assertThat(content.path(0).path("source").path("media_type").asText()).isEqualTo("image/png");
      assertThat(content.path(0).path("source").path("data").asText()).isEqualTo("AQI=");
      assertThat(content.path(1).path("text").asText()).doesNotContain("The user wants to know");
    }

    @Test
    @DisplayName("identifyPlant folds a non-blank user context into the prompt")
    void identifyPlantWithContext() throws Exception {
      server.enqueue(text("{}"));
      server.enqueue(text("{}"));

      client.identifyPlant(new byte[] {1}, "image/jpeg", "why are the leaves yellow");
      client.identifyPlant(new byte[] {1}, "image/jpeg", "   ");

      assertThat(userText(server.takeRequest(), 1))
          .contains("The user wants to know: why are the leaves yellow.");
      assertThat(userText(server.takeRequest(), 1)).doesNotContain("The user wants to know");
    }

    @Test
    @DisplayName("text-only calls use the matching system prompt and default a null species")
    void textCalls() throws Exception {
      server.enqueue(text("advice"));
      server.enqueue(text("description"));
      server.enqueue(text("reply"));
      server.enqueue(text("{}"));
      server.enqueue(text("{\"regions\":[]}"));

      assertThat(client.generateCureAdvice(null, "Leaf spot")).isEqualTo("advice");
      assertThat(client.generateDiseaseDescription("Rose", "Black spot")).isEqualTo("description");
      assertThat(client.chat("garden context", "hello")).isEqualTo("reply");
      client.generateSpeciesEnrichment("Ficus lyrata", null);
      client.analyzeRegions(new byte[] {1}, "image/jpeg");

      RecordedRequest cure = server.takeRequest();
      assertThat(body(cure).path("system").asText())
          .isEqualTo(DeepSeekClient.CURE_ADVICE_SYSTEM_PROMPT);
      assertThat(userText(cure, 0)).startsWith("My Unknown plant has the following issue");
      assertThat(body(server.takeRequest()).path("system").asText())
          .isEqualTo(DeepSeekClient.DISEASE_DESCRIPTION_SYSTEM_PROMPT);
      assertThat(body(server.takeRequest()).path("system").asText()).isEqualTo("garden context");
      assertThat(userText(server.takeRequest(), 0)).isEqualTo("Scientific name: Ficus lyrata");
      assertThat(body(server.takeRequest()).path("system").asText())
          .isEqualTo(GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("joins multiple text blocks, ignores non-text blocks and strips fences")
    void joinsTextBlocks() {
      server.enqueue(
          json(
              200,
              "{\"stop_reason\":\"end_turn\",\"content\":["
                  + "{\"type\":\"thinking\",\"thinking\":\"hmm\"},"
                  + "{\"type\":\"text\",\"text\":\"```json\\n{\\\"a\\\":\"},"
                  + "{\"type\":\"text\",\"text\":\"1}\\n```\"}]}"));

      assertThat(client.chat("s", "m")).isEqualTo("{\"a\":1}");
    }
  }

  @Nested
  @DisplayName("availability and errors")
  class Errors {

    @Test
    @DisplayName("an un-keyed client is unavailable and fails before any network call")
    void unkeyed() {
      AnthropicClient unkeyed = new AnthropicClient(baseUrl(), " ", MODEL);

      assertThat(unkeyed.isAvailable()).isFalse();
      assertThat(client.isAvailable()).isTrue();
      assertThat(client.getDefaultModel()).isEqualTo(MODEL);
      assertThatThrownBy(() -> unkeyed.chat("s", "m"))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("not configured");
      assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("the HTTP layer waits out Retry-After and retries a 429 once before failing")
    void rateLimitedRetriesOnceThenSucceeds() {
      server.enqueue(json(429, "{}").setHeader("retry-after", "1"));
      server.enqueue(text("after retry"));

      assertThat(client.chat("s", "m")).isEqualTo("after retry");
      assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a 429 that persists after the retry carries Retry-After, or 60s without one")
    void rateLimitedTwice() {
      server.enqueue(json(429, "{}").setHeader("retry-after", "1"));
      server.enqueue(json(429, "{}").setHeader("retry-after", "1"));
      server.enqueue(json(429, "{}"));
      server.enqueue(json(429, "{}"));

      assertThatThrownBy(() -> client.chat("s", "m"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1L));
      assertThatThrownBy(() -> client.chat("s", "m"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
      assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("other upstream errors become a 503")
    void upstreamError() {
      server.enqueue(json(401, "{\"error\":\"invalid x-api-key\"}"));

      assertThatThrownBy(() -> client.generateCureAdvice("Rose", "Rust"))
          .isInstanceOfSatisfying(
              PlantPalException.class,
              e -> {
                assertThat(e).isNotInstanceOf(RateLimitException.class);
                assertThat(e.getErrorCode()).isEqualTo(503);
                assertThat(e).hasMessage("Cure advice unavailable");
              });
    }

    @Test
    @DisplayName("an empty content array is an empty response")
    void emptyContent() {
      server.enqueue(json(200, "{\"stop_reason\":\"end_turn\",\"content\":[]}"));

      assertThatThrownBy(() -> client.chat("s", "m"))
          .hasMessage("Empty response from chat service");
    }

    @Test
    @DisplayName("content with no text blocks is an empty response, not a blank success")
    void noTextBlocks() {
      server.enqueue(
          json(
              200,
              "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"x\"}]}"));

      assertThatThrownBy(() -> client.chat("s", "m"))
          .hasMessage("Empty response from chat service");
    }

    @Test
    @DisplayName("a max_tokens-truncated reply is still returned (logged as truncated)")
    void truncatedReplyReturned() {
      server.enqueue(
          json(
              200,
              "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"partial\"}]}"));

      assertThat(client.chat("s", "m")).isEqualTo("partial");
    }

    @Test
    @DisplayName("an unreachable API becomes a 503")
    void unreachable() throws IOException {
      server.shutdown();

      assertThatThrownBy(() -> client.chat("s", "m"))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(503));
    }
  }

  private String baseUrl() {
    String url = server.url("/").toString();
    return url.substring(0, url.length() - 1);
  }

  private JsonNode body(RecordedRequest request) throws IOException {
    return objectMapper.readTree(request.getBody().clone().readString(StandardCharsets.UTF_8));
  }

  private String userText(RecordedRequest request, int block) throws IOException {
    return body(request).path("messages").path(0).path("content").path(block).path("text").asText();
  }

  private MockResponse text(String text) {
    try {
      String block = objectMapper.writeValueAsString(text);
      return json(
          200,
          "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":"
              + block
              + "}]}");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
