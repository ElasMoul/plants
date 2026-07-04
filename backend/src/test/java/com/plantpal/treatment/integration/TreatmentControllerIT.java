package com.plantpal.treatment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("TreatmentController — Integration Tests")
class TreatmentControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private IdentificationRepository identificationRepository;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
  @MockBean private GitHubModelsClient gitHubModelsClient;
  @MockBean private DeepSeekClient deepSeekClient;
  @MockBean private OllamaClient ollamaClient;

  private String tokenA;

  @BeforeEach
  void setUp() {
    tokenA = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
    // Fire-and-forget disease-description generation — degrades to null on failure (by design),
    // never blocks createTreatment()'s response either way, but stub it so logs stay quiet.
    when(deepSeekClient.generateDiseaseDescription(any(), any()))
        .thenReturn("A common fungal issue.");
    when(deepSeekClient.generateCureAdvice(any(), any()))
        .thenReturn(
            """
            {"advice":"Apply neem oil weekly.",\
            "actionPlan":{"type":"TREATMENT","steps":[\
            {"order":1,"instruction":"Apply neem oil","dueOffsetDays":0}]}}
            """);
  }

  @Test
  @DisplayName("POST /treatments should create a DRAFT treatment for an owned plant")
  void shouldCreateDraftTreatment() {
    int plantId = createPlant(tokenA);
    long identificationId = seedIdentification(plantId);

    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "identificationId",
            identificationId,
            "diseaseName",
            "Powdery mildew");

    ResponseEntity<Map> response = postWithToken("/api/v1/treatments", request, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data.get("status")).isEqualTo("DRAFT");
    assertThat(data.get("diseaseName")).isEqualTo("Powdery mildew");
  }

  @Test
  @DisplayName(
      "POST /treatments should reject a second active treatment for the same plant+disease")
  void shouldRejectDuplicateActiveTreatment() {
    int plantId = createPlant(tokenA);
    long identificationId = seedIdentification(plantId);
    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "identificationId",
            identificationId,
            "diseaseName",
            "Powdery mildew");
    postWithToken("/api/v1/treatments", request, tokenA);

    ResponseEntity<Map> response = postWithToken("/api/v1/treatments", request, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("POST /treatments should reject a plant the caller does not own")
  void shouldRejectUnownedPlant() {
    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
    int plantId = createPlant(tokenB);
    long identificationId = seedIdentification(plantId);

    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "identificationId",
            identificationId,
            "diseaseName",
            "Powdery mildew");
    ResponseEntity<Map> response = postWithToken("/api/v1/treatments", request, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /{id} should return 404 for a treatment owned by another user")
  void shouldReturn404ForOtherUsersTreatment() {
    int plantId = createPlant(tokenA);
    long identificationId = seedIdentification(plantId);
    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "identificationId",
            identificationId,
            "diseaseName",
            "Powdery mildew");
    ResponseEntity<Map> createResponse = postWithToken("/api/v1/treatments", request, tokenA);
    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
    int treatmentId = (int) created.get("id");

    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
    ResponseEntity<Map> response = getWithToken("/api/v1/treatments/" + treatmentId, tokenB);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName(
      "PATCH /{id}/complete should reject completing a DRAFT (not yet IN_PROGRESS) treatment")
  void shouldRejectCompletingDraftTreatment() {
    int plantId = createPlant(tokenA);
    long identificationId = seedIdentification(plantId);
    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "identificationId",
            identificationId,
            "diseaseName",
            "Powdery mildew");
    ResponseEntity<Map> createResponse = postWithToken("/api/v1/treatments", request, tokenA);
    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
    int treatmentId = (int) created.get("id");

    ResponseEntity<Map> response =
        restTemplate.exchange(
            url("/api/v1/treatments/" + treatmentId + "/complete"),
            HttpMethod.PATCH,
            authEntity(tokenA),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("GET /plants/{id}/active-treatment should return 404 when none exists")
  void shouldReturn404WhenNoActiveTreatment() {
    int plantId = createPlant(tokenA);

    ResponseEntity<Map> response =
        getWithToken("/api/v1/plants/" + plantId + "/active-treatment", tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("POST /treatments without a token should return 401")
  void shouldReturn401WhenUnauthenticated() {
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/treatments"), Map.of(), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private int createPlant(String token) {
    ResponseEntity<Map> response =
        postWithToken("/api/v1/plants", Map.of("nickname", "My Plant"), token);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    return (int) data.get("id");
  }

  private long seedIdentification(int plantId) {
    Identification identification =
        Identification.builder()
            .plantId((long) plantId)
            .userId(1L)
            .photoUrl("/photos/test.jpg")
            .status(IdentificationStatus.COMPLETED)
            .identificationStatus(IdentificationStageStatus.COMPLETED)
            .annotationStatus(IdentificationStageStatus.SKIPPED)
            .candidateStatus(IdentificationStageStatus.SKIPPED)
            .scientificName("Monstera deliciosa")
            .build();
    return identificationRepository.save(identification).getId();
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

  private ResponseEntity<Map> postWithToken(String path, Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Content-Type", "application/json");
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    return restTemplate.exchange(url(path), HttpMethod.POST, entity, Map.class);
  }

  private ResponseEntity<Map> getWithToken(String path, String token) {
    return restTemplate.exchange(url(path), HttpMethod.GET, authEntity(token), Map.class);
  }

  private HttpEntity<Void> authEntity(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return new HttpEntity<>(headers);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }
}
