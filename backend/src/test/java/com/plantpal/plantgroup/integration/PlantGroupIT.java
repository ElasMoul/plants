package com.plantpal.plantgroup.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
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

@DisplayName("Plant groups - Integration Tests")
class PlantGroupIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    tokenA = registerAndLogin();
    tokenB = registerAndLogin();
  }

  @Test
  @DisplayName("create, filter, bulk care, then delete keeps the plants ungrouped")
  void fullLifecycle() {
    int group = idOf(call(HttpMethod.POST, "/api/v1/groups", Map.of("name", "Greenhouse"), tokenA));
    int p1 = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "One"), tokenA));
    int p2 = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "Two"), tokenA));
    int p3 = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "Loose"), tokenA));
    int archived =
        idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "Gone"), tokenA));
    for (int id : new int[] {p1, p2, archived}) {
      assertThat(
              call(
                      HttpMethod.PUT,
                      "/api/v1/plants/" + id + "/group",
                      Map.of("groupId", group),
                      tokenA)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    call(HttpMethod.DELETE, "/api/v1/plants/" + archived, null, tokenA);

    assertThat(plantIds("/api/v1/plants?groupId=" + group, tokenA))
        .containsExactlyInAnyOrder(p1, p2);
    assertThat(plantIds("/api/v1/plants", tokenA)).containsExactlyInAnyOrder(p1, p2, p3);
    assertThat(plantIds("/api/v1/plants?groupId=" + group, tokenB)).isEmpty();

    ResponseEntity<Map> care =
        call(
            HttpMethod.POST,
            "/api/v1/groups/" + group + "/care",
            Map.of("action", "WATERED"),
            tokenA);
    assertThat(care.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat((List<?>) care.getBody().get("data")).hasSize(2);
    call(HttpMethod.POST, "/api/v1/groups/" + group + "/care", Map.of("action", "FED"), tokenA);
    assertThat(careTypes(p1)).containsExactlyInAnyOrder("WATERING", "FERTILIZING");
    assertThat(careTypes(archived)).isEmpty();

    assertThat(call(HttpMethod.DELETE, "/api/v1/groups/" + group, null, tokenA).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(plantIds("/api/v1/plants", tokenA)).containsExactlyInAnyOrder(p1, p2, p3);
    assertThat(plantIds("/api/v1/plants?groupId=" + group, tokenA)).isEmpty();
    Map<?, ?> plant = data(call(HttpMethod.GET, "/api/v1/plants/" + p1, null, tokenA));
    assertThat(plant.get("status")).isEqualTo("ACTIVE");
    assertThat(plant.get("groupId")).isNull();
  }

  @Test
  @DisplayName("another user gets 404 on rename, delete, care and assign; list is scoped")
  void ownershipIsEnforced() {
    int group = idOf(call(HttpMethod.POST, "/api/v1/groups", Map.of("name", "Mine"), tokenA));
    int plantA = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "A"), tokenA));
    int plantB = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "B"), tokenB));

    assertThat(
            call(HttpMethod.PUT, "/api/v1/groups/" + group, Map.of("name", "X"), tokenB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.DELETE, "/api/v1/groups/" + group, null, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/v1/groups/" + group + "/care",
                    Map.of("action", "FED"),
                    tokenB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/api/v1/plants/" + plantB + "/group",
                    Map.of("groupId", group),
                    tokenB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/api/v1/plants/" + plantA + "/group",
                    Map.of("groupId", group),
                    tokenB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    ResponseEntity<Map> list = call(HttpMethod.GET, "/api/v1/groups", null, tokenB);
    assertThat((List<?>) data(list).get("content")).isEmpty();
  }

  @Test
  @DisplayName("validation and authentication")
  void validationAndAuth() {
    assertThat(call(HttpMethod.POST, "/api/v1/groups", Map.of("name", " "), tokenA).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(HttpMethod.POST, "/api/v1/groups", Map.of("name", "x".repeat(101)), tokenA)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(restTemplate.getForEntity(url("/api/v1/groups"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private List<Integer> plantIds(String path, String token) {
    List<Map<String, Object>> content =
        (List<Map<String, Object>>) data(call(HttpMethod.GET, path, null, token)).get("content");
    return content.stream().map(m -> (Integer) m.get("id")).toList();
  }

  private List<String> careTypes(int plantId) {
    List<Map<String, Object>> content =
        (List<Map<String, Object>>)
            data(call(HttpMethod.GET, "/api/v1/care/plant/" + plantId, null, tokenA))
                .get("content");
    return content.stream().map(m -> (String) m.get("careType")).toList();
  }

  private static Map<?, ?> data(ResponseEntity<Map> response) {
    return (Map<?, ?>) response.getBody().get("data");
  }

  private static int idOf(ResponseEntity<Map> response) {
    return (int) data(response).get("id");
  }

  private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("Content-Type", "application/json");
    return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), Map.class);
  }

  private String registerAndLogin() {
    var request =
        Map.of(
            "email",
            "it-" + UUID.randomUUID() + "@example.com",
            "password",
            "password123",
            "firstName",
            "Test",
            "lastName",
            "User");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);
    return (String) data(response).get("token");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
