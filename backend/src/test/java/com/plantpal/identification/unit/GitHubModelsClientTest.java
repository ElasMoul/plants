package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
      org.assertj.core.api.Assertions.assertThat(result).contains("Monstera deliciosa");
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

      org.assertj.core.api.Assertions.assertThat(result).contains("Monstera deliciosa");
      org.assertj.core.api.Assertions.assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should throw RateLimitException immediately on 429 without retrying")
    void shouldThrowRateLimitOnH429WithoutRetry() {
      client = clientWithBudget(40_000);
      mockWebServer.enqueue(new MockResponse().setResponseCode(429));

      byte[] image = "img".getBytes(StandardCharsets.UTF_8);

      assertThatThrownBy(() -> client.identifyPlant(image, MediaType.IMAGE_JPEG_VALUE))
          .isInstanceOf(RateLimitException.class);

      org.assertj.core.api.Assertions.assertThat(mockWebServer.getRequestCount())
          .as("429 must not trigger a retry")
          .isEqualTo(1);
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

      org.assertj.core.api.Assertions.assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
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

      org.assertj.core.api.Assertions.assertThat(result).isEqualTo("{\"regions\":[]}");
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
  }
}
