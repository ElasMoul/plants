package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.shared.exception.PlantPalException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    ollamaClient =
        new OllamaClient(server.url("/").toString(), MODEL, 5, 10, "10m", new ObjectMapper());
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
      assertThat(body).contains("\"keep_alive\":\"10m\"");
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

  // ── chatStream() ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("chatStream()")
  class ChatStream {

    @Test
    @DisplayName("should invoke the callback once per streamed chunk, in order")
    void shouldInvokeCallbackPerChunk() {
      // Given — Ollama's NDJSON streaming format: one JSON object per line
      String body =
          """
          {"message":{"role":"assistant","content":"A "},"done":false}
          {"message":{"role":"assistant","content":"Monstera "},"done":false}
          {"message":{"role":"assistant","content":"deliciosa."},"done":false}
          {"message":{"role":"assistant","content":""},"done":true}
          """;
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/x-ndjson")
              .setBody(body));

      // When
      List<String> tokens = new ArrayList<>();
      ollamaClient.chatStream("Identify this plant", tokens::add);

      // Then
      assertThat(tokens).containsExactly("A ", "Monstera ", "deliciosa.", "");
      assertThat(String.join("", tokens)).isEqualTo("A Monstera deliciosa.");
    }

    @Test
    @DisplayName("should send stream:true in the request body")
    void shouldRequestStreamingMode() throws InterruptedException {
      // Given
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/x-ndjson")
              .setBody(
                  "{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"done\":true}\n"));

      // When
      ollamaClient.chatStream("prompt", token -> {});

      // Then
      RecordedRequest request = server.takeRequest();
      assertThat(request.getBody().readUtf8()).contains("\"stream\":true");
    }

    @Test
    @DisplayName("should throw PlantPalException(503) when Ollama is unreachable")
    void shouldThrow503WhenUnreachable() throws IOException {
      // Given
      server.shutdown();

      // When / Then
      assertThatThrownBy(() -> ollamaClient.chatStream("prompt", token -> {}))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }
  }

  @Nested
  @DisplayName("chatStream() — Ollama-reported errors")
  class ChatStreamErrors {

    @Test
    @DisplayName("an HTTP error status (e.g. model not pulled) fails instead of an empty reply")
    void httpErrorStatusFails() {
      server.enqueue(
          new MockResponse()
              .setResponseCode(404)
              .addHeader("Content-Type", "application/json")
              .setBody("{\"error\":\"model 'phi3' not found, try pulling it first\"}"));

      List<String> tokens = new ArrayList<>();
      assertThatThrownBy(() -> ollamaClient.chatStream("prompt", tokens::add))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
      assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("an error line mid-stream fails instead of silently truncating")
    void midStreamErrorFails() {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/x-ndjson")
              .setBody(
                  "{\"message\":{\"role\":\"assistant\",\"content\":\"A \"},\"done\":false}\n"
                      + "{\"error\":\"out of memory\"}\n"));

      // Spring Boot's ObjectMapper ignores unknown properties, so "error" must be modelled
      // explicitly rather than relying on a strict mapper to reject the line.
      OllamaClient springLike =
          new OllamaClient(
              server.url("/").toString(),
              MODEL,
              5,
              10,
              "10m",
              new ObjectMapper()
                  .configure(
                      com.fasterxml.jackson.databind.DeserializationFeature
                          .FAIL_ON_UNKNOWN_PROPERTIES,
                      false));

      List<String> tokens = new ArrayList<>();
      assertThatThrownBy(() -> springLike.chatStream("prompt", tokens::add))
          .isInstanceOf(PlantPalException.class);
      assertThat(tokens).containsExactly("A ");
    }

    @Test
    @DisplayName("a malformed stream line is a 503")
    void malformedLineFails() {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/x-ndjson")
              .setBody("not json\n"));

      assertThatThrownBy(() -> ollamaClient.chatStream("prompt", token -> {}))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }
  }

  @Nested
  @DisplayName("text helpers")
  class TextHelpers {

    @Test
    @DisplayName("a reply with a null content is an empty response")
    void nullContentIsEmpty() {
      server.enqueue(jsonResponse("{\"message\":{\"role\":\"assistant\"},\"done\":true}"));

      assertThatThrownBy(() -> ollamaClient.chat("prompt"))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);
    }

    @Test
    @DisplayName("enrichment, cure advice and disease description prepend their system prompt")
    void textHelpersUseSystemPrompts() throws Exception {
      server.enqueue(chatReply("```json\n{\"description\":\"d\"}\n```"));
      server.enqueue(chatReply("<think>x</think>{\"advice\":\"a\"}"));
      server.enqueue(chatReply("It is a fungus."));

      assertThat(ollamaClient.generateSpeciesEnrichment("Ficus lyrata", "Fiddle-leaf fig"))
          .isEqualTo("{\"description\":\"d\"}");
      assertThat(ollamaClient.generateCureAdvice(null, "Rust")).isEqualTo("{\"advice\":\"a\"}");
      assertThat(ollamaClient.generateDiseaseDescription("Rose", "Rust"))
          .isEqualTo("It is a fungus.");

      assertThat(sentBody()).contains("expert botanist").contains("Common name: Fiddle-leaf fig");
      assertThat(sentBody()).contains("plant pathologist").contains("My Unknown plant has");
      assertThat(sentBody()).contains("Disease/pest issue: Rust");
      assertThat(ollamaClient.getModel()).isEqualTo(MODEL);
    }
  }

  @Nested
  @DisplayName("vision (/api/generate)")
  class Vision {

    @Test
    @DisplayName("identifyPlant sends a resized JPEG, keep_alive and the user context")
    void identifyPlant() throws Exception {
      server.enqueue(generateReply("{\"species\":\"Ficus\"}"));
      server.enqueue(generateReply("{\"species\":\"Ficus\"}"));

      assertThat(ollamaClient.identifyPlant(pngBytes(), "image/png", "brown tips"))
          .isEqualTo("{\"species\":\"Ficus\"}");
      ollamaClient.identifyPlant(pngBytes(), "image/png");

      RecordedRequest first = server.takeRequest();
      assertThat(first.getPath()).isEqualTo("/api/generate");
      var json = new ObjectMapper().readTree(first.getBody().readUtf8());
      assertThat(json.path("model").asText()).isEqualTo(MODEL);
      assertThat(json.path("keep_alive").asText()).isEqualTo("10m");
      assertThat(json.path("stream").asBoolean()).isFalse();
      assertThat(json.path("images")).hasSize(1);
      assertThat(json.path("prompt").asText()).contains("The user wants to know: brown tips.");
      assertThat(sentBody()).doesNotContain("The user wants to know");
    }

    @Test
    @DisplayName("analyzeRegions uses the annotation prompt")
    void analyzeRegions() throws Exception {
      server.enqueue(generateReply("{\"regions\":[]}"));

      assertThat(ollamaClient.analyzeRegions(pngBytes(), "image/png"))
          .isEqualTo("{\"regions\":[]}");
      assertThat(sentBody()).contains("identify all plant and disease regions");
    }

    @Test
    @DisplayName("blank vision replies are 502, unreachable Ollama is 503")
    void visionFailures() throws IOException {
      byte[] image = pngBytes();
      server.enqueue(generateReply("  "));
      server.enqueue(generateReply(""));

      assertThatThrownBy(() -> ollamaClient.identifyPlant(image, "image/png"))
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);
      assertThatThrownBy(() -> ollamaClient.analyzeRegions(image, "image/png"))
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);

      server.shutdown();
      assertThatThrownBy(() -> ollamaClient.identifyPlant(image, "image/png"))
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
      assertThatThrownBy(() -> ollamaClient.analyzeRegions(image, "image/png"))
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }
  }

  private String sentBody() throws InterruptedException {
    return server.takeRequest().getBody().readUtf8();
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  private static MockResponse chatReply(String content) throws IOException {
    return jsonResponse(
        "{\"message\":{\"role\":\"assistant\",\"content\":"
            + new ObjectMapper().writeValueAsString(content)
            + "},\"done\":true}");
  }

  private static MockResponse generateReply(String response) throws IOException {
    return jsonResponse(
        "{\"response\":" + new ObjectMapper().writeValueAsString(response) + ",\"done\":true}");
  }

  private static byte[] pngBytes() throws IOException {
    var image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    return out.toByteArray();
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
