package com.plantpal.journal.integration;

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

@DisplayName("Plant journal events - Integration Tests")
class JournalEventIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String tokenA;
  private String tokenB;
  private int plantA;

  @BeforeEach
  void setUp() {
    tokenA = registerAndLogin();
    tokenB = registerAndLogin();
    plantA = idOf(call(HttpMethod.POST, "/api/v1/plants", Map.of("nickname", "Fern"), tokenA));
  }

  @Test
  @DisplayName("all five types can be added with and without a note")
  void addAllTypes() {
    for (String type : List.of("REPOTTING", "PRUNING", "FLOWERING", "FRUITING", "PROPAGATION")) {
      ResponseEntity<Map> withNote =
          call(
              HttpMethod.POST,
              base(plantA),
              Map.of("type", type, "eventDate", "2026-05-01", "note", "n"),
              tokenA);
      assertThat(withNote.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(data(withNote)).containsEntry("type", type).containsEntry("note", "n");
    }
    ResponseEntity<Map> noNote =
        call(
            HttpMethod.POST,
            base(plantA),
            Map.of("type", "PRUNING", "eventDate", "2026-05-02"),
            tokenA);
    assertThat(noNote.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(data(noNote).get("note")).isNull();
    assertThat(content(call(HttpMethod.GET, base(plantA), null, tokenA))).hasSize(6);
  }

  @Test
  @DisplayName("missing type, missing date or unknown type is a 400")
  void validation() {
    assertThat(
            call(HttpMethod.POST, base(plantA), Map.of("eventDate", "2026-05-01"), tokenA)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(HttpMethod.POST, base(plantA), Map.of("type", "PRUNING"), tokenA).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.POST,
                    base(plantA),
                    Map.of("type", "WATERING", "eventDate", "2026-05-01"),
                    tokenA)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("list is ordered by event date regardless of insertion order")
  void listOrderedByDate() {
    for (String date : List.of("2026-03-10", "2026-01-05", "2026-02-20")) {
      call(HttpMethod.POST, base(plantA), Map.of("type", "PRUNING", "eventDate", date), tokenA);
    }
    List<String> dates =
        content(call(HttpMethod.GET, base(plantA), null, tokenA)).stream()
            .map(m -> (String) m.get("eventDate"))
            .toList();
    assertThat(dates).containsExactly("2026-01-05", "2026-02-20", "2026-03-10");
  }

  @Test
  @DisplayName("owner can edit and delete; the event is gone afterwards")
  void ownerEditsAndDeletes() {
    int id =
        idOf(
            call(
                HttpMethod.POST,
                base(plantA),
                Map.of("type", "PRUNING", "eventDate", "2026-05-01"),
                tokenA));
    ResponseEntity<Map> put =
        call(
            HttpMethod.PUT,
            base(plantA) + "/" + id,
            Map.of("type", "FRUITING", "eventDate", "2026-06-01", "note", "ripe"),
            tokenA);
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(put)).containsEntry("type", "FRUITING").containsEntry("note", "ripe");

    assertThat(call(HttpMethod.DELETE, base(plantA) + "/" + id, null, tokenA).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(content(call(HttpMethod.GET, base(plantA), null, tokenA))).isEmpty();
  }

  @Test
  @DisplayName("another user cannot list, add, edit or delete; the event is unchanged")
  void accessRule() {
    int id =
        idOf(
            call(
                HttpMethod.POST,
                base(plantA),
                Map.of("type", "REPOTTING", "eventDate", "2026-05-01", "note", "mine"),
                tokenA));
    String path = base(plantA) + "/" + id;
    Map<String, String> body = Map.of("type", "PRUNING", "eventDate", "2026-07-01");

    assertThat(call(HttpMethod.GET, base(plantA), null, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.POST, base(plantA), body, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.PUT, path, body, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(call(HttpMethod.DELETE, path, null, tokenB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    List<Map<String, Object>> events = content(call(HttpMethod.GET, base(plantA), null, tokenA));
    assertThat(events).hasSize(1);
    assertThat(events.get(0))
        .containsEntry("type", "REPOTTING")
        .containsEntry("eventDate", "2026-05-01")
        .containsEntry("note", "mine");
  }

  @Test
  @DisplayName("unauthenticated calls get 401")
  void unauthenticated() {
    assertThat(restTemplate.getForEntity(url(base(plantA)), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private static String base(int plantId) {
    return "/api/v1/plants/" + plantId + "/journal-events";
  }

  private static List<Map<String, Object>> content(ResponseEntity<Map> response) {
    return (List<Map<String, Object>>) data(response).get("content");
  }

  private static Map<String, Object> data(ResponseEntity<Map> response) {
    return (Map<String, Object>) response.getBody().get("data");
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
