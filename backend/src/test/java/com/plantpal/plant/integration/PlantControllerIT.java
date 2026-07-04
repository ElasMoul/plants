package com.plantpal.plant.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
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

@DisplayName("PlantController — Integration Tests")
class PlantControllerIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String tokenA;

  @BeforeEach
  void setUp() {
    tokenA = registerAndLogin(uniqueEmail(), "password123");
  }

  @Test
  @DisplayName("Full CRUD lifecycle: create → get → update → archive → 404")
  void shouldManagePlantLifecycle() {
    // Create
    var createRequest = Map.of("nickname", "My Monstera", "location", "Living Room");
    ResponseEntity<Map> createResponse = postWithToken("/api/v1/plants", createRequest, tokenA);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
    int plantId = (int) created.get("id");
    assertThat(created.get("nickname")).isEqualTo("My Monstera");
    assertThat(created.get("status")).isEqualTo("ACTIVE");

    // Get
    ResponseEntity<Map> getResponse = getWithToken("/api/v1/plants/" + plantId, tokenA);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    @SuppressWarnings("unchecked")
    Map<String, Object> fetched = (Map<String, Object>) getResponse.getBody().get("data");
    assertThat(fetched.get("id")).isEqualTo(plantId);

    // Update
    var updateRequest = Map.of("nickname", "Monstera Deluxe", "location", "Bedroom");
    ResponseEntity<Map> updateResponse =
        putWithToken("/api/v1/plants/" + plantId, updateRequest, tokenA);
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    @SuppressWarnings("unchecked")
    Map<String, Object> updated = (Map<String, Object>) updateResponse.getBody().get("data");
    assertThat(updated.get("nickname")).isEqualTo("Monstera Deluxe");

    // Archive (soft delete)
    ResponseEntity<Void> deleteResponse = deleteWithToken("/api/v1/plants/" + plantId, tokenA);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // GET after archive → 404 (ACTIVE plants only)
    ResponseEntity<Map> afterArchiveResponse = getWithToken("/api/v1/plants/" + plantId, tokenA);
    assertThat(afterArchiveResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET another user's plant should return 404 — not 403 — to conceal existence")
  void shouldReturn404WhenAccessingOtherUsersPlant() {
    // Given — userA creates a plant
    var createRequest = Map.of("nickname", "UserA's Fern");
    ResponseEntity<Map> createResponse = postWithToken("/api/v1/plants", createRequest, tokenA);

    @SuppressWarnings("unchecked")
    Map<String, Object> created = (Map<String, Object>) createResponse.getBody().get("data");
    int plantId = (int) created.get("id");

    // Given — a different user (userB)
    String tokenB = registerAndLogin(uniqueEmail(), "password123");

    // When — userB tries to access userA's plant
    ResponseEntity<Map> response = getWithToken("/api/v1/plants/" + plantId, tokenB);

    // Then — 404, not 403, so userB can't infer that the plant exists
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /plants without a token should return 401")
  void shouldReturn401WhenUnauthenticated() {
    // When — no Authorization header
    ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/v1/plants"), Map.class);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String registerAndLogin(String email, String password) {
    var request =
        Map.of("email", email, "password", password, "firstName", "Test", "lastName", "User");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    return (String) data.get("token");
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

  private ResponseEntity<Map> putWithToken(String path, Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Content-Type", "application/json");
    HttpEntity<Object> entity = new HttpEntity<>(body, headers);
    return restTemplate.exchange(url(path), HttpMethod.PUT, entity, Map.class);
  }

  private ResponseEntity<Void> deleteWithToken(String path, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    return restTemplate.exchange(url(path), HttpMethod.DELETE, entity, Void.class);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }
}
