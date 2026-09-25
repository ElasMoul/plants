package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.DeepSeekAnnotationClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("GitHubModelsClient — Unit Tests")
class GitHubModelsClientTest {

  private MockWebServer mockWebServer;
  private GitHubModelsClient client;

  private static final String VALID_IDENTIFICATION_RESPONSE =
      """
      {
        "choices": [
          {
            "message": {
              "content": "{\\"species\\":\\"Monstera deliciosa\\",\\"commonName\\":\\"Swiss Cheese Plant\\",\\"confidence\\":\\"HIGH\\",\\"healthStatus\\":\\"HEALTHY\\",\\"healthNotes\\":null,\\"carePlan\\":{\\"wateringFrequencyDays\\":7,\\"fertilizingFrequencyDays\\":30,\\"repottingFrequencyMonths\\":12,\\"careCards\\":[],\\"beginnerWarnings\\":[]}}"
            }
          }
        ]
      }
      """;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  private GitHubModelsClient clientWithBudget(int tokenBudget) {
    return new GitHubModelsClient(
        mockWebServer.url("/").toString(),
        "test-token",
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4.1",
        tokenBudget);
  }

  @Nested
  @DisplayName("Token budget")
  class TokenBudget {

    @Test
    @DisplayName(
        "should throw RateLimitException immediately when token budget is exhausted before HTTP call")
    void shouldThrowRateLimitWhenBudgetExhausted() {
      // Given a client with a budget of 1 token — a 30-byte image → ~10 estimated tokens
      client = clientWithBudget(1);
      byte[] image = new byte[30];

      // When/Then — throws before any HTTP call (MockWebServer has no enqueued response)
      assertThatThrownBy(() -> client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOf(RateLimitException.class)
          .hasMessageContaining("token budget exhausted");
    }

    @Test
    @DisplayName("should succeed when token budget is sufficient for the image")
    void shouldSucceedWhenBudgetSufficient() {
      // Given a client with ample budget and a valid response
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(VALID_IDENTIFICATION_RESPONSE)
              .addHeader("Content-Type", "application/json"));

      byte[] image = "smallimage".getBytes(StandardCharsets.UTF_8); // ~3 tokens estimate

      // When/Then — no exception
      String result = client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE);
      assertThat(result).contains("Monstera deliciosa");
    }
  }

  @Nested
  @DisplayName("Retry on connection failure")
  class RetryOnConnectionFailure {

    @Test
    @DisplayName("should retry once on non-429 HTTP error and succeed on second attempt")
    void shouldRetryOnHttpErrorAndSucceedOnSecondAttempt() {
      client = clientWithBudget(40_000);
      // First response: 503, second: success
      mockWebServer.enqueue(new MockResponse().setResponseCode(503));
      mockWebServer.enqueue(
          new MockResponse()
              .setBody(VALID_IDENTIFICATION_RESPONSE)
              .addHeader("Content-Type", "application/json"));

      byte[] image = "img".getBytes(StandardCharsets.UTF_8);
      String result = client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE);

      assertThat(result).contains("Monstera deliciosa");
      assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should throw RateLimitException immediately on 429 without retrying")
    void shouldThrowRateLimitOnH429WithoutRetry() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(new MockResponse().setResponseCode(429));

      byte[] image = "img".getBytes(StandardCharsets.UTF_8);

      assertThatThrownBy(() -> client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOf(RateLimitException.class);

      assertThat(mockWebServer.getRequestCount()).as("429 must not trigger a retry").isEqualTo(1);
    }

    @Test
    @DisplayName("should throw PlantPalException after both attempts fail")
    void shouldThrowAfterBothAttemptsFail() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(new MockResponse().setResponseCode(503));
      mockWebServer.enqueue(new MockResponse().setResponseCode(503));

      byte[] image = "img".getBytes(StandardCharsets.UTF_8);

      assertThatThrownBy(() -> client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOf(PlantPalException.class);

      assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Request shape and annotation")
  class RequestShapeAndAnnotation {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("gpt-4.1 routing, user context and model getters")
    void gpt41WithUserContext() throws Exception {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(jsonResponse(VALID_IDENTIFICATION_RESPONSE));
      mockWebServer.enqueue(jsonResponse(VALID_IDENTIFICATION_RESPONSE));
      byte[] image = "img".getBytes(StandardCharsets.UTF_8);

      client.identifyPlantWithGpt41(image, MediaType.IMAGE_PNG_VALUE, "yellow leaves");
      client.identifyPlantWithGpt41(image, MediaType.IMAGE_PNG_VALUE);

      var first = sentJson();
      assertThat(first.path("model").asText()).isEqualTo("gpt-4.1");
      var content = first.path("messages").path(1).path("content");
      assertThat(content.path(0).path("image_url").path("url").asText())
          .startsWith("data:image/png;base64,");
      assertThat(content.path(1).path("text").asText())
          .contains("The user wants to know: yellow leaves.");
      assertThat(sentJson().path("messages").path(1).path("content").path(1).path("text").asText())
          .doesNotContain("The user wants to know");
      assertThat(client.getIdentificationModel()).isEqualTo("gpt-4o");
      assertThat(client.getGpt41Model()).isEqualTo("gpt-4.1");
      assertThat(client.getAnnotationModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("a 429 carries GitHub's own \"wait N seconds\" hint")
    void rateLimitCarriesUpstreamWait() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(429)
              .setBody(
                  "Rate limit of 1 per 60s exceeded. Please wait 37 seconds before retrying."));

      assertThatThrownBy(() -> client.identifyPlant("img".getBytes(), MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(37L));
    }

    @Test
    @DisplayName("empty choices fail the identification without a retry")
    void emptyChoices() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(jsonResponse("{\"choices\":[]}"));

      assertThatThrownBy(() -> client.identifyPlant("img".getBytes(), MediaType.IMAGE_JPEG_VALUE))
          .hasMessage("Empty response from identification service");
      assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("analyzeRegions uses the annotation model and strips a fenced reply")
    void analyzeRegions() throws Exception {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(
          jsonResponse(
              "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"regions\\\":[]}\\n```\"}}]}"));

      String result = client.analyzeRegions("img".getBytes(), MediaType.IMAGE_JPEG_VALUE);

      assertThat(result).isEqualTo("{\"regions\":[]}");
      var json = sentJson();
      assertThat(json.path("model").asText()).isEqualTo("gpt-4o-mini");
      assertThat(json.path("messages").path(0).path("content").asText())
          .isEqualTo(GitHubModelsClient.ANNOTATION_SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("analyzeRegions maps empty, HTTP-error and unreachable replies to a 503")
    void analyzeRegionsFailures() throws IOException {
      client = clientWithBudget(40_000);
      byte[] image = "img".getBytes();
      mockWebServer.enqueue(jsonResponse("{\"choices\":[]}"));
      mockWebServer.enqueue(new MockResponse().setResponseCode(500));

      assertThatThrownBy(() -> client.analyzeRegions(image, MediaType.IMAGE_JPEG_VALUE))
          .hasMessage("Empty response from annotation service");
      assertThatThrownBy(() -> client.analyzeRegions(image, MediaType.IMAGE_JPEG_VALUE))
          .hasMessage("Annotation service unavailable");
      mockWebServer.shutdown();
      assertThatThrownBy(() -> client.analyzeRegions(image, MediaType.IMAGE_JPEG_VALUE))
          .hasMessage("Annotation service unavailable");
    }

    @Test
    @DisplayName(
        "an annotation 429 surfaces as a rate limit, and the annotation client won't retry it")
    void analyzeRegionsRateLimit() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("wait 9 seconds"));

      DeepSeekAnnotationClient annotationClient = new DeepSeekAnnotationClient(client);

      assertThatThrownBy(
              () -> annotationClient.analyzeRegions("img".getBytes(), MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOfSatisfying(
              RateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(9L));
      assertThat(mockWebServer.getRequestCount()).as("429 must not be retried").isEqualTo(1);
    }

    @Test
    @DisplayName("analyzeRegions respects the token budget")
    void analyzeRegionsBudget() {
      client = clientWithBudget(1);

      assertThatThrownBy(() -> client.analyzeRegions(new byte[3000], MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOf(RateLimitException.class);
      assertThat(mockWebServer.getRequestCount()).isZero();
    }

    private JsonNode sentJson() throws Exception {
      return objectMapper.readTree(
          mockWebServer.takeRequest().getBody().readString(StandardCharsets.UTF_8));
    }

    private MockResponse jsonResponse(String body) {
      return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
  }

  @Nested
  @DisplayName("DeepSeekAnnotationClient retry with jitter")
  class AnnotationRetry {

    @Test
    @DisplayName("should retry annotation once on non-429 exception and succeed")
    void shouldRetryAnnotationAndSucceed() {
      GitHubModelsClient mockGitHub = mock(GitHubModelsClient.class);
      when(mockGitHub.analyzeRegions(any(), any()))
          .thenThrow(new RuntimeException("EOF"))
          .thenReturn("{\"regions\":[]}");

      DeepSeekAnnotationClient annotationClient = new DeepSeekAnnotationClient(mockGitHub);
      String result = annotationClient.analyzeRegions(new byte[] {1, 2, 3}, "image/jpeg");

      assertThat(result).isEqualTo("{\"regions\":[]}");
      verify(mockGitHub, times(2)).analyzeRegions(any(), any());
    }

    @Test
    @DisplayName("should not retry annotation on RateLimitException (429)")
    void shouldNotRetryAnnotationOn429() {
      GitHubModelsClient mockGitHub = mock(GitHubModelsClient.class);
      when(mockGitHub.analyzeRegions(any(), any()))
          .thenThrow(new RateLimitException("token budget exhausted", 60L));

      DeepSeekAnnotationClient annotationClient = new DeepSeekAnnotationClient(mockGitHub);

      assertThatThrownBy(() -> annotationClient.analyzeRegions(new byte[] {1, 2, 3}, "image/jpeg"))
          .isInstanceOf(RateLimitException.class);

      verify(mockGitHub, times(1)).analyzeRegions(any(), any());
    }

    @Test
    @DisplayName("should give up with a 500 after two non-429 failures")
    void shouldGiveUpAfterTwoFailures() {
      GitHubModelsClient mockGitHub = mock(GitHubModelsClient.class);
      when(mockGitHub.analyzeRegions(any(), any()))
          .thenThrow(new PlantPalException("Annotation service unavailable", 503));

      DeepSeekAnnotationClient annotationClient = new DeepSeekAnnotationClient(mockGitHub);

      assertThatThrownBy(() -> annotationClient.analyzeRegions(new byte[] {1}, "image/jpeg"))
          .isInstanceOfSatisfying(
              PlantPalException.class,
              e -> {
                assertThat(e.getErrorCode()).isEqualTo(500);
                assertThat(e).hasMessageContaining("Annotation service unavailable");
              });
      verify(mockGitHub, times(2)).analyzeRegions(any(), any());
    }
  }
}
