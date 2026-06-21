package com.plantpal.reminder.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.identification.client.DeepSeekClient;
import com.plantpal.identification.client.GitHubModelsClient;
import com.plantpal.identification.client.OllamaClient;
import java.util.List;
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

/**
 * POST /treatment-plans creates from a literal ActionPlanDto body -- no AI call on this endpoint --
 * so the AI client/Kafka mocks below exist only to keep the ApplicationContext cacheable across
 * this and the other new controller ITs (same @MockBean set = same cached context).
 */
@DisplayName("TreatmentPlanController — Integration Tests")
class TreatmentPlanControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
  @MockBean private GitHubModelsClient gitHubModelsClient;
  @MockBean private DeepSeekClient deepSeekClient;
  @MockBean private OllamaClient ollamaClient;

  private String tokenA;

  @BeforeEach
  void setUp() {
    tokenA = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
  }

  @Test
  @DisplayName("POST should create a treatment plan with one reminder per step, for an owned plant")
  void shouldCreateTreatmentPlanForOwnedPlant() {
    int plantId = createPlant(tokenA, "My Monstera");

    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "title",
            "Treat spider mites",
            "sourceCareCardType",
            "PEST",
            "actionPlan",
            Map.of(
                "type",
                "TREATMENT",
                "steps",
                List.of(
                    Map.of("order", 1, "instruction", "Apply neem oil", "dueOffsetDays", 0),
                    Map.of("order", 2, "instruction", "Re-inspect", "dueOffsetDays", 3))));

    ResponseEntity<Map> response = postWithToken("/api/v1/treatment-plans", request, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    assertThat(data.get("title")).isEqualTo("Treat spider mites");
    assertThat(data.get("status")).isEqualTo("ACTIVE");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
    assertThat(steps).hasSize(2);
  }

  @Test
  @DisplayName("POST should reject a plan for a plant the caller does not own")
  void shouldRejectPlanForUnownedPlant() {
    String tokenB = (String) registerAndLogin(uniqueEmail(), "password123").get("token");
    int plantId = createPlant(tokenB, "Someone Else's Plant");

    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "title",
            "Treat spider mites",
            "sourceCareCardType",
            "PEST",
            "actionPlan",
            Map.of(
                "type",
                "TREATMENT",
                "steps",
                List.of(Map.of("order", 1, "instruction", "Apply neem oil", "dueOffsetDays", 0))));

    ResponseEntity<Map> response = postWithToken("/api/v1/treatment-plans", request, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /{id} should return the plan and its steps when owned by the caller")
  void shouldReturnOwnedTreatmentPlan() {
    int plantId = createPlant(tokenA, "My Monstera");
    Map<String, Object> request =
        Map.of(
            "plantId",
            plantId,
            "title",
            "Treat spider mites",
            "sourceCareCardType",
            "PEST",
            "actionPlan",
            Map.of(
                "type",
                "TREATMENT",
                "steps",
                List.of(Map.of("order", 1, "instruction", "Apply neem oil", "dueOffsetDays", 0))));
    ResponseEntity<Map> createResponse = postWithToken("/api/v1/treatment-plans", request, tokenA);
    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
    int planId = (int) created.get("id");

    ResponseEntity<Map> response = getWithToken("/api/v1/treatment-plans/" + planId, tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("GET /{id} should return 404 for a nonexistent treatment plan")
  void shouldReturn404ForNonexistentPlan() {
    ResponseEntity<Map> response = getWithToken("/api/v1/treatment-plans/999999", tokenA);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("POST /treatment-plans without a token should return 401")
  void shouldReturn401WhenUnauthenticated() {
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/treatment-plans"), Map.of(), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private int createPlant(String token, String nickname) {
    ResponseEntity<Map> response =
        postWithToken("/api/v1/plants", Map.of("nickname", nickname), token);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    return (int) data.get("id");
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
