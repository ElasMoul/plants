package com.plantpal.identification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeepSeekClient - Unit Tests")
class DeepSeekClientTest {

  private static final String R1 = "DeepSeek-R1";
  private static final String O4_MINI = "o4-mini";
  private static final String GPT41_MINI = "gpt-4.1-mini";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private HttpServer server;
  private DeepSeekClient client;
  private int status = 200;
  private String retryAfterHeader;
  private String responseBody = completion("{\"advice\":\"ok\"}");

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          if (retryAfterHeader != null) {
            exchange.getResponseHeaders().set("Retry-After", retryAfterHeader);
          }
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    client = newClient("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Nested
  @DisplayName("request shape per model")
  class RequestShape {

    @Test
    @DisplayName("DeepSeek-R1 cure advice sends temperature, JSON mode and bearer token")
    void r1CureAdviceUsesTemperatureAndJsonMode() throws Exception {
      String result = client.generateCureAdvice("Monstera deliciosa", "Leaf spot");

      assertThat(result).isEqualTo("{\"advice\":\"ok\"}");
      JsonNode json = sentJson();
      assertThat(authorization.get()).isEqualTo("Bearer test-token");
      assertThat(json.path("model").asText()).isEqualTo(R1);
      assertThat(json.path("temperature").asDouble()).isEqualTo(0.3);
      assertThat(json.has("max_completion_tokens")).isFalse();
      assertThat(json.path("response_format").path("type").asText()).isEqualTo("json_object");
      assertThat(json.path("messages").path(1).path("content").asText())
          .contains("Monstera deliciosa")
          .contains("Leaf spot");
    }

    @Test
    @DisplayName("o4-mini omits temperature and caps max_completion_tokens")
    void o4MiniUsesMaxCompletionTokens() throws Exception {
      client.generateCureAdviceViaO4Mini("Ficus", "Scale");

      JsonNode json = sentJson();
      assertThat(json.path("model").asText()).isEqualTo(O4_MINI);
      assertThat(json.has("temperature")).isFalse();
      assertThat(json.path("max_completion_tokens").asInt()).isEqualTo(4096);
    }

    @Test
    @DisplayName("gpt-4.1-mini keeps temperature")
    void gpt41MiniKeepsTemperature() throws Exception {
      client.generateCureAdviceViaGpt41Mini("Ficus", "Scale");

      JsonNode json = sentJson();
      assertThat(json.path("model").asText()).isEqualTo(GPT41_MINI);
      assertThat(json.path("temperature").asDouble()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("disease description is plain text (no JSON mode) and defaults a null species")
    void diseaseDescriptionIsPlainText() throws Exception {
      responseBody = completion("Powdery mildew is a fungal disease.");

      String result = client.generateDiseaseDescription(null, "Powdery mildew");

      assertThat(result).isEqualTo("Powdery mildew is a fungal disease.");
      JsonNode json = sentJson();
      assertThat(json.has("response_format")).isFalse();
      assertThat(json.path("messages").path(1).path("content").asText())
          .isEqualTo("Plant: Unknown plant\nDisease/pest issue: Powdery mildew");
    }

    @Test
    @DisplayName("disease description routes through o4-mini and gpt-4.1-mini")
    void diseaseDescriptionAlternateModels() throws Exception {
      client.generateDiseaseDescriptionViaO4Mini("Rose", "Black spot");
      assertThat(sentJson().path("model").asText()).isEqualTo(O4_MINI);

      client.generateDiseaseDescriptionViaGpt41Mini("Rose", "Black spot");
      assertThat(sentJson().path("model").asText()).isEqualTo(GPT41_MINI);
    }

    @Test
    @DisplayName("species enrichment omits the common name line when absent")
    void speciesEnrichmentWithoutCommonName() throws Exception {
      client.generateSpeciesEnrichment("Ficus lyrata", null);

      assertThat(sentJson().path("messages").path(1).path("content").asText())
          .isEqualTo("Scientific name: Ficus lyrata");

      client.generateSpeciesEnrichment("Ficus lyrata", "Fiddle-leaf fig");
      assertThat(sentJson().path("messages").path(1).path("content").asText())
          .isEqualTo("Scientific name: Ficus lyrata\nCommon name: Fiddle-leaf fig");
    }

    @Test
    @DisplayName("duplicate care-card check uses low temperature and returns stripped JSON")
    void duplicateCareCards() throws Exception {
      responseBody = completion("<think>compare</think>{\"duplicateGroups\":[]}");

      String result = client.detectDuplicateCareCards("[]");

      assertThat(result).isEqualTo("{\"duplicateGroups\":[]}");
      assertThat(sentJson().path("temperature").asDouble()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("care plan falls back to species for a missing common name")
    void carePlanDefaults() throws Exception {
      client.generateCarePlan("Pothos", null, null);

      assertThat(sentJson().path("messages").path(1).path("content").asText())
          .isEqualTo("Plant: Pothos (Pothos)\nHealth notes: No issues noted");
    }
  }

  @Nested
  @DisplayName("error handling")
  class ErrorHandling {

    @Test
    @DisplayName("429 with a numeric Retry-After header carries that wait")
    void rateLimitFromHeader() {
      status = 429;
      retryAfterHeader = "42";
      responseBody = "{}";

      assertThatThrownBy(() -> client.generateCureAdvice("Fern", "Rot"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(42L));
    }

    @Test
    @DisplayName("429 without a header parses the wait from the body")
    void rateLimitFromBody() {
      status = 429;
      responseBody = "{\"error\":\"Rate limit exceeded. Please wait 17 seconds before retrying.\"}";

      assertThatThrownBy(() -> client.generateSpeciesEnrichment("Fern", null))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(17L));
    }

    @Test
    @DisplayName("429 with an unparseable header and body falls back to 60s")
    void rateLimitDefault() {
      status = 429;
      retryAfterHeader = "Wed, 21 Oct 2026 07:28:00 GMT";
      responseBody = "{}";

      assertThatThrownBy(() -> client.detectDuplicateCareCards("[]"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
    }

    @Test
    @DisplayName("a negative Retry-After header never yields a negative wait")
    void negativeRetryAfterIsIgnored() {
      status = 429;
      retryAfterHeader = "-5";
      responseBody = "{}";

      assertThatThrownBy(() -> client.generateCureAdvice("Fern", "Rot"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
    }

    @Test
    @DisplayName("an absurdly long wait in the body falls back instead of overflowing")
    void overflowingBodyWaitFallsBack() {
      status = 429;
      responseBody = "{\"error\":\"wait 99999999999999999999 seconds\"}";

      assertThatThrownBy(() -> client.generateCureAdvice("Fern", "Rot"))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60L));
    }

    @Test
    @DisplayName("other upstream errors become a 503")
    void serverErrorBecomes503() {
      status = 500;
      responseBody = "{\"error\":\"boom\"}";

      assertThatThrownBy(() -> client.generateCarePlan("Pothos", "Pothos", "ok"))
          .isInstanceOfSatisfying(
              PlantPalException.class,
              e -> {
                assertThat(e).isNotInstanceOf(RateLimitException.class);
                assertThat(e.getErrorCode()).isEqualTo(503);
              });
    }

    @Test
    @DisplayName("empty choices are rejected on every call path")
    void emptyChoicesRejected() {
      responseBody = "{\"choices\":[]}";

      assertThatThrownBy(() -> client.generateCureAdvice("Fern", "Rot"))
          .hasMessage("Empty response from cure advice service");
      assertThatThrownBy(() -> client.generateSpeciesEnrichment("Fern", null))
          .hasMessage("Empty response from species enrichment service");
      assertThatThrownBy(() -> client.generateCarePlan("Fern", null, null))
          .hasMessage("Empty response from care plan service");
      assertThatThrownBy(() -> client.detectDuplicateCareCards("[]"))
          .hasMessage("Empty response from duplicate care card check");
    }

    @Test
    @DisplayName("an unreachable host becomes a 503 on every call path")
    void unreachableHostBecomes503() {
      server.stop(0);
      DeepSeekClient offline = newClient("http://127.0.0.1:" + server.getAddress().getPort());

      assertThatThrownBy(() -> offline.generateDiseaseDescription("Fern", "Rot"))
          .hasMessage("Disease description unavailable");
      assertThatThrownBy(() -> offline.generateSpeciesEnrichment("Fern", null))
          .hasMessage("Species enrichment unavailable");
      assertThatThrownBy(() -> offline.generateCarePlan("Fern", null, null))
          .hasMessage("Care plan service unavailable");
      assertThatThrownBy(() -> offline.detectDuplicateCareCards("[]"))
          .hasMessage("Duplicate care card check unavailable");
    }
  }

  @Nested
  @DisplayName("stripThinkTags()")
  class StripThinkTags {

    @Test
    @DisplayName("passes null and plain content through (trimmed)")
    void plainContent() {
      assertThat(DeepSeekClient.stripThinkTags(null)).isNull();
      assertThat(DeepSeekClient.stripThinkTags("  {\"a\":1}\n")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("drops a leading <think> block")
    void dropsThinkBlock() {
      assertThat(DeepSeekClient.stripThinkTags("<think>\nreasoning\n</think>\n\n{\"a\":1}"))
          .isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("unwraps a ```json fence, also after a <think> block")
    void unwrapsFence() {
      assertThat(DeepSeekClient.stripThinkTags("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
      assertThat(DeepSeekClient.stripThinkTags("<think>x</think>```\n{\"a\":1}\n```"))
          .isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("unwraps a fence followed by trailing prose")
    void fenceWithTrailingProse() {
      assertThat(DeepSeekClient.stripThinkTags("```json\n{\"a\":1}\n```\nHope this helps!"))
          .isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("unwraps a single-line fence")
    void singleLineFence() {
      assertThat(DeepSeekClient.stripThinkTags("```{\"a\":1}```")).isEqualTo("{\"a\":1}");
      assertThat(DeepSeekClient.stripThinkTags("```json {\"a\":1}```")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("keeps JSON that starts on the fence line instead of dropping its first line")
    void jsonStartingOnFenceLine() {
      assertThat(DeepSeekClient.stripThinkTags("```{\n\"a\":1\n}\n```")).isEqualTo("{\n\"a\":1\n}");
    }

    @Test
    @DisplayName("keeps the body of an unclosed (truncated) fence")
    void unclosedFence() {
      assertThat(DeepSeekClient.stripThinkTags("```json\n{\"a\":1")).isEqualTo("{\"a\":1");
    }
  }

  @Test
  @DisplayName("exposes the configured model ids for the gateway modelHint")
  void exposesModelIds() {
    assertThat(client.getModel()).isEqualTo(R1);
    assertThat(client.getO4MiniModel()).isEqualTo(O4_MINI);
    assertThat(client.getGpt41MiniModel()).isEqualTo(GPT41_MINI);
  }

  private static DeepSeekClient newClient(String baseUrl) {
    return new DeepSeekClient(baseUrl, "test-token", R1, O4_MINI, GPT41_MINI);
  }

  private JsonNode sentJson() throws Exception {
    return objectMapper.readTree(requestBody.get());
  }

  private String completion(String content) {
    try {
      return objectMapper.writeValueAsString(
          Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
