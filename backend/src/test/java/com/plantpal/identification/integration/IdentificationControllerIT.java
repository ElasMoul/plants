package com.plantpal.identification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStageStatus;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.repository.IdentificationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Exercises the HTTP/security/persistence/ownership layer only -- the AI clients and Kafka are
 * mocked so this never makes a real external call or requires a running broker. The actual
 * AI-pipeline behavior (processIdentification(), parsing, etc.) is already covered by
 * IdentificationServiceImplTest's unit tests.
 */
@DisplayName("IdentificationController — Integration Tests")
class IdentificationControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private IdentificationRepository identificationRepository;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
  @MockBean private GitHubModelsClient gitHubModelsClient;
  @MockBean private DeepSeekClient deepSeekClient;
  @MockBean private OllamaClient ollamaClient;

  private String tokenA;
  private Long userIdA;

  @BeforeEach
  void setUp() {
    Map<String, Object> auth = registerAndLogin(uniqueEmail(), "password123");
    tokenA = (String) auth.get("token");
    userIdA = ((Number) auth.get("userId")).longValue();
  }

  @Test
  @DisplayName("POST /analyze should persist a PENDING identification and return 202")
  void shouldSubmitAndReturnPending() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("images", imagePart());

    ResponseEntity<Map> response = postMultipart("/api/v1/identifications/analyze", body, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data.get("status")).isEqualTo("PENDING");

    int id = (int) data.get("identificationId");
    Identification saved = identificationRepository.findById((long) id).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(IdentificationStatus.PENDING);
    assertThat(saved.getUserId()).isEqualTo(userIdA);
  }

  @Test
  @DisplayName("GET /{id} should return the identification when owned by the caller")
  void shouldReturnOwnedIdentification() {
    Identification saved = seedIdentification(userIdA);

    ResponseEntity<Map> response = getWithToken("/api/v1/identifications/" + saved.getId(), tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(((Number) data.get("id")).longValue()).isEqualTo(saved.getId());
  }

  @Test
  @DisplayName("GET /{id} should return 404 — not 403 — for another user's identification")
  void shouldReturn404ForOtherUsersIdentification() {
    Identification saved = seedIdentification(userIdA);
    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");

    ResponseEntity<Map> response = getWithToken("/api/v1/identifications/" + saved.getId(), tokenB);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /{id} should return 404 for a nonexistent identification")
  void shouldReturn404ForNonexistentIdentification() {
    ResponseEntity<Map> response = getWithToken("/api/v1/identifications/999999", tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /api/v1/identifications without a token should return 401")
  void shouldReturn401WhenUnauthenticated() {
    ResponseEntity<Map> response =
        restTemplate.getForEntity(url("/api/v1/identifications"), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Identification seedIdentification(Long userId) {
    Identification identification =
        Identification.builder()
            .userId(userId)
            .photoUrl("/photos/test.jpg")
            .status(IdentificationStatus.COMPLETED)
            .identificationStatus(IdentificationStageStatus.COMPLETED)
            .annotationStatus(IdentificationStageStatus.SKIPPED)
            .candidateStatus(IdentificationStageStatus.SKIPPED)
            .scientificName("Monstera deliciosa")
            .commonName("Swiss cheese plant")
            .build();
    return identificationRepository.save(identification);
  }

  private org.springframework.core.io.ByteArrayResource imagePart() {
    byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    return new org.springframework.core.io.ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return "test.jpg";
      }
    };
  }

  private Map<String, Object> registerAndLogin(String email, String password) {
    var request =
        Map.of("email", email, "password", password, "firstName", "Test", "lastName", "User");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    return data;
  }

  private ResponseEntity<Map> postMultipart(
      String path, MultiValueMap<String, Object> body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
    return restTemplate.exchange(url(path), HttpMethod.POST, entity, Map.class);
  }

  private ResponseEntity<Map> getWithToken(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(url(path), HttpMethod.GET, entity, Map.class);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }
}
