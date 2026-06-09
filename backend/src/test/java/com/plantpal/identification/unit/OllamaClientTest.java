package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantpal.identification.client.OllamaClient;
import com.plantpal.shared.exception.PlantPalException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OllamaClient — Unit Tests")
class OllamaClientTest {

  private static final String MODEL = "phi3";
  private static final String ASSISTANT_REPLY = "A Monstera deliciosa.";

  private MockWebServer server;
  private OllamaClient ollamaClient;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    ollamaClient = new OllamaClient(server.url("/").toString(), MODEL);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("chat() — success")
  class ChatSuccess {

    @Test
    @DisplayName("should return the assistant content when Ollama responds successfully")
    void shouldReturnAssistantContent() throws InterruptedException {
      // Given
      server.enqueue(ollamaResponse(ASSISTANT_REPLY));

      // When
      String result = ollamaClient.chat("Identify this plant");

      // Then
      assertThat(result).isEqualTo(ASSISTANT_REPLY);
    }

    @Test
    @DisplayName("should POST to /api/chat with the correct model and role")
    void shouldSendCorrectRequest() throws InterruptedException {
      // Given
      server.enqueue(ollamaResponse(ASSISTANT_REPLY));

      // When
      ollamaClient.chat("What plant is this?");

      // Then
      RecordedRequest request = server.takeRequest();
      assertThat(request.getMethod()).isEqualTo("POST");
      assertThat(request.getPath()).isEqualTo("/api/chat");
      assertThat(request.getHeader("Content-Type")).contains("application/json");

      String body = request.getBody().readUtf8();
      assertThat(body).contains("\"model\":\"" + MODEL + "\"");
      assertThat(body).contains("\"role\":\"user\"");
      assertThat(body).contains("\"stream\":false");
    }

    @Test
    @DisplayName("should pass the exact prompt text in the message content")
    void shouldPassPromptVerbatim() throws InterruptedException {
      // Given
      String prompt = "Is this plant healthy?";
      server.enqueue(ollamaResponse(ASSISTANT_REPLY));

      // When
      ollamaClient.chat(prompt);

      // Then
      RecordedRequest request = server.takeRequest();
      assertThat(request.getBody().readUtf8()).contains(prompt);
    }
  }

  // ── Error paths ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("chat() — error handling")
  class ChatErrors {

    @Test
    @DisplayName(
        "should throw PlantPalException(502) when Ollama returns a body with no message field")
    void shouldThrow502WhenMessageFieldMissing() {
      // Given — valid HTTP 200 but Ollama omits the message object
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/json")
              .setBody("{\"done\":true}"));

      // When / Then
      assertThatThrownBy(() -> ollamaClient.chat("prompt"))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("Empty response")
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);
    }

    @Test
    @DisplayName("should throw PlantPalException(502) when Ollama returns an empty body")
    void shouldThrow502WhenBodyIsEmpty() {
      // Given
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/json")
              .setBody("{}"));

      // When / Then
      assertThatThrownBy(() -> ollamaClient.chat("prompt"))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);
    }

    @Test
    @DisplayName("should throw PlantPalException(503) when Ollama server is unreachable")
    void shouldThrow503WhenOllamaIsDown() throws IOException {
      // Given — shut down the server to simulate Ollama not running
      server.shutdown();

      // When / Then
      assertThatThrownBy(() -> ollamaClient.chat("prompt"))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("AI service unavailable")
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }

    @Test
    @DisplayName("should throw PlantPalException(503) when Ollama returns a server error (5xx)")
    void shouldThrow503WhenOllamaReturns500() {
      // Given
      server.enqueue(
          new MockResponse()
              .setResponseCode(500)
              .addHeader("Content-Type", "application/json")
              .setBody("{\"error\":\"model not loaded\"}"));

      // When / Then
      assertThatThrownBy(() -> ollamaClient.chat("prompt"))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private MockResponse ollamaResponse(String content) {
    String json =
        "{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},\"done\":true}";
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(json);
  }
}
