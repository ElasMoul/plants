package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantpal.identification.client.PlantNetDiseaseClient;
import com.plantpal.identification.client.PlantNetDiseaseClient.ByteArrayMultipartFile;
import com.plantpal.identification.dto.plantnet.PlantNetDiseaseResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("PlantNetDiseaseClient — Unit Tests")
class PlantNetDiseaseClientTest {

  private static final String DISEASE_RESPONSE =
      """
      {
        "results": [
          { "name": "Powdery mildew", "description": "Fungal", "score": 0.81, "eppo": "ERYSSP" }
        ],
        "remainingIdentificationRequests": 42
      }
      """;

  private static final List<String> AUTO = List.of("auto");
  private final List<MultipartFile> oneImage = List.of(image("leaf.jpg"));
  private MockWebServer server;
  private PlantNetDiseaseClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client = new PlantNetDiseaseClient(server.url("/").toString(), "test-key", 6, "en");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Nested
  @DisplayName("identifyDisease()")
  class IdentifyDisease {

    @Test
    @DisplayName("posts multipart images with api key, lang and nb-results; parses results")
    void happyPath() throws Exception {
      server.enqueue(json(200, DISEASE_RESPONSE));

      PlantNetDiseaseResponse response =
          client.identifyDisease(List.of(image("leaf.jpg")), List.of("leaf"), "fr");

      assertThat(response.results()).hasSize(1);
      assertThat(response.results().get(0).name()).isEqualTo("Powdery mildew");
      assertThat(response.remainingIdentificationRequests()).isEqualTo(42);

      RecordedRequest request = server.takeRequest();
      assertThat(request.getPath())
          .startsWith("/v2/diseases/identify?")
          .contains("api-key=test-key")
          .contains("include-related-images=true")
          .contains("nb-results=6")
          .contains("lang=fr");
      String body = request.getBody().readString(StandardCharsets.UTF_8);
      assertThat(body).contains("filename=\"leaf.jpg\"").contains("name=\"organs\"");
    }

    @Test
    @DisplayName("defaults lang and organs when absent")
    void defaultsLangAndOrgans() throws Exception {
      server.enqueue(json(200, DISEASE_RESPONSE));

      client.identifyDisease(List.of(image("leaf.jpg")), null, " ");

      RecordedRequest request = server.takeRequest();
      assertThat(request.getPath()).contains("lang=en");
      assertThat(request.getBody().readString(StandardCharsets.UTF_8)).contains("auto");
    }

    @Test
    @DisplayName("404 means no corroboration — empty result, not an error")
    void notFoundIsEmpty() {
      server.enqueue(json(404, "{\"message\":\"Species not found\"}"));

      PlantNetDiseaseResponse response =
          client.identifyDisease(List.of(image("leaf.jpg")), List.of("auto"), "en");

      assertThat(response.results()).isEmpty();
    }

    @Test
    @DisplayName("429 surfaces as a rate-limit error")
    void quotaExhausted() {
      server.enqueue(json(429, "{}"));

      assertThatThrownBy(() -> client.identifyDisease(oneImage, AUTO, "en"))
          .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("other upstream errors surface as 502")
    void serverError() {
      server.enqueue(json(500, "{}"));

      assertThatThrownBy(() -> client.identifyDisease(oneImage, AUTO, "en"))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(502));
    }

    @Test
    @DisplayName("an unreachable PlantNet degrades to an empty result")
    void unreachableIsEmpty() throws IOException {
      server.shutdown();

      PlantNetDiseaseResponse response =
          client.identifyDisease(List.of(image("leaf.jpg")), List.of("auto"), "en");

      assertThat(response.results()).isEmpty();
    }

    @Test
    @DisplayName("an empty body degrades to an empty result")
    void emptyBodyIsEmpty() {
      server.enqueue(new MockResponse().setResponseCode(200));

      PlantNetDiseaseResponse response =
          client.identifyDisease(List.of(image("leaf.jpg")), List.of("auto"), "en");

      assertThat(response.results()).isEmpty();
    }
  }

  @Nested
  @DisplayName("input validation")
  class Validation {

    @Test
    @DisplayName("rejects no images, null images and more than five")
    void imageCount() {
      assertThatThrownBy(() -> client.identifyDisease(null, null, "en"))
          .isInstanceOf(ValidationException.class);
      assertThatThrownBy(() -> client.identifyDisease(List.of(), null, "en"))
          .isInstanceOf(ValidationException.class);
      List<MultipartFile> six = Collections.nCopies(6, image("leaf.jpg"));
      assertThatThrownBy(() -> client.identifyDisease(six, null, "en"))
          .isInstanceOf(ValidationException.class);
      assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("rejects a payload over 50 MB before any network call")
    void payloadSize() {
      MultipartFile huge =
          new MockMultipartFile("images", "big.jpg", "image/jpeg", new byte[1]) {
            @Override
            public long getSize() {
              return 51L * 1024 * 1024;
            }
          };

      List<MultipartFile> hugeOnly = List.of(huge);
      assertThatThrownBy(() -> client.identifyDisease(hugeOnly, null, "en"))
          .isInstanceOf(ValidationException.class);
      assertThat(server.getRequestCount()).isZero();
    }
  }

  @Test
  @DisplayName("ByteArrayMultipartFile exposes the wrapped bytes")
  void byteArrayMultipartFile() throws IOException {
    ByteArrayMultipartFile file = new ByteArrayMultipartFile(new byte[] {1, 2, 3}, "image/png");

    assertThat(file.getName()).isEqualTo("image");
    assertThat(file.getOriginalFilename()).isEqualTo("image.jpg");
    assertThat(file.getContentType()).isEqualTo("image/png");
    assertThat(file.isEmpty()).isFalse();
    assertThat(file.getSize()).isEqualTo(3);
    assertThat(file.getBytes()).containsExactly(1, 2, 3);
    assertThat(file.getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    assertThat(new ByteArrayMultipartFile(new byte[0], "image/png").isEmpty()).isTrue();
    java.io.File target = new java.io.File("x");
    assertThatThrownBy(() -> file.transferTo(target))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static MockMultipartFile image(String name) {
    return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1, 2, 3});
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
