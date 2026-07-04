package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.shared.exception.PlantPalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("PlantNetClient — Unit Tests")
class PlantNetClientTest {

  private MockWebServer mockWebServer;
  private PlantNetClient plantNetClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String VALID_RESPONSE =
      """
      {
        "results": [
          {
            "score": 0.92,
            "species": {
              "scientificNameWithoutAuthor": "Monstera deliciosa",
              "commonNames": ["Swiss cheese plant"],
              "genus": { "scientificNameWithoutAuthor": "Monstera" },
              "family": { "scientificNameWithoutAuthor": "Araceae" }
            }
          },
          {
            "score": 0.05,
            "species": {
              "scientificNameWithoutAuthor": "Monstera adansonii",
              "commonNames": ["Monkey leaf"],
              "genus": { "scientificNameWithoutAuthor": "Monstera" },
              "family": { "scientificNameWithoutAuthor": "Araceae" }
            }
          }
        ],
        "remainingIdentificationRequests": 499
      }
      """;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = mockWebServer.url("/").toString();
    plantNetClient = new PlantNetClient(baseUrl, "test-api-key", "all", 6, "en");
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile(
        "images", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  @Nested
  @DisplayName("identify()")
  class Identify {

    @Test
    @DisplayName("should return mapped PlantNetResponse on successful identification")
    void shouldReturnResponseOnSuccess() throws Exception {
      mockWebServer.enqueue(
          new MockResponse().setBody(VALID_RESPONSE).addHeader("Content-Type", "application/json"));

      PlantNetResponse response = plantNetClient.identify(List.of(validImage()), List.of("leaf"));

      assertThat(response).isNotNull();
      assertThat(response.results()).hasSize(2);
      assertThat(response.results().get(0).score()).isEqualTo(0.92);
      assertThat(response.results().get(0).species().scientificNameWithoutAuthor())
          .isEqualTo("Monstera deliciosa");
      assertThat(response.results().get(0).species().commonNames())
          .containsExactly("Swiss cheese plant");
      assertThat(response.remainingIdentificationRequests()).isEqualTo(499);
    }

    @Test
    @DisplayName("should send correct multipart request structure")
    void shouldSendCorrectRequestStructure() throws Exception {
      mockWebServer.enqueue(
          new MockResponse().setBody(VALID_RESPONSE).addHeader("Content-Type", "application/json"));

      plantNetClient.identify(List.of(validImage()), List.of("flower"));

      RecordedRequest request = mockWebServer.takeRequest();
      assertThat(request.getPath()).contains("api-key=test-api-key");
      assertThat(request.getPath()).contains("/v2/identify/all");
      assertThat(request.getHeader("Content-Type")).contains("multipart/form-data");
      String body = request.getBody().readUtf8();
      assertThat(body).contains("plant.jpg");
      assertThat(body).contains("flower");
    }

    @Test
    @DisplayName("should default organs to 'auto' when organs list is null")
    void shouldDefaultOrgansToAutoWhenNull() throws Exception {
      mockWebServer.enqueue(
          new MockResponse().setBody(VALID_RESPONSE).addHeader("Content-Type", "application/json"));

      plantNetClient.identify(List.of(validImage()), null);

      RecordedRequest request = mockWebServer.takeRequest();
      String body = request.getBody().readUtf8();
      assertThat(body).contains("auto");
    }

    @Test
    @DisplayName("should throw PlantPalException with code 404 when PlantNet returns 404")
    void shouldThrow404WhenNoMatchFound() {
      mockWebServer.enqueue(new MockResponse().setResponseCode(404));

      assertThatThrownBy(() -> plantNetClient.identify(List.of(validImage()), List.of("leaf")))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("No plant species match found")
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(404);
    }

    @Test
    @DisplayName(
        "should deserialize predictedOrgans as [{organ,score}] objects (T8.C regression fix)"
            + " — fixture also feeds Phase 9 T9.8 eval corpus")
    void shouldDeserializePredictedOrgansAsObjects() throws Exception {
      String fixture =
          new String(
              getClass()
                  .getClassLoader()
                  .getResourceAsStream("fixtures/plantnet_identify_response.json")
                  .readAllBytes(),
              StandardCharsets.UTF_8);
      mockWebServer.enqueue(
          new MockResponse().setBody(fixture).addHeader("Content-Type", "application/json"));

      PlantNetResponse response = plantNetClient.identify(List.of(validImage()), List.of("leaf"));

      assertThat(response.predictedOrgans()).isNotEmpty();
      assertThat(response.predictedOrgans().get(0).organ()).isNotNull();
      assertThat(response.predictedOrgans().get(0).score()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("should throw PlantPalException with code 503 on connection failure")
    void shouldThrow503OnConnectionFailure() throws IOException {
      mockWebServer.shutdown();

      assertThatThrownBy(() -> plantNetClient.identify(List.of(validImage()), List.of("leaf")))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(503);
    }

    @Test
    @DisplayName("should throw PlantPalException with code 502 when PlantNet returns 500")
    void shouldThrow503OnServerError() {
      mockWebServer.enqueue(new MockResponse().setResponseCode(500));

      assertThatThrownBy(() -> plantNetClient.identify(List.of(validImage()), List.of("leaf")))
          .isInstanceOf(PlantPalException.class)
          .extracting(e -> ((PlantPalException) e).getErrorCode())
          .isEqualTo(502);
    }
  }
}
