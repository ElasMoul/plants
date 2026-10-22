package com.plantpal.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.AbstractIntegrationTest;
import com.plantpal.export.service.ExportFileStore;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStageStatus;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.journal.entity.JournalEventType;
import com.plantpal.journal.entity.PlantJournalEvent;
import com.plantpal.journal.repository.PlantJournalEventRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.repository.CareLogRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("ExportController - Integration Tests")
class ExportControllerIT extends AbstractIntegrationTest {

  private static final Path EXPORT_DIR = createExportDir();
  private static final String EXPORT_PATH = "/api/v1/users/me/export";

  @DynamicPropertySource
  static void exportProperties(DynamicPropertyRegistry registry) {
    registry.add("app.export.directory", EXPORT_DIR::toString);
    registry.add("app.export.rate-limit-per-hour", () -> 3);
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private PlantRepository plantRepository;
  @Autowired private CareLogRepository careLogRepository;
  @Autowired private IdentificationRepository identificationRepository;
  @Autowired private PlantJournalEventRepository journalRepository;
  @Autowired private ExportFileStore fileStore;
  @Autowired private ObjectMapper objectMapper;

  @Value("${app.storage.local-path}")
  private String photoDir;

  private record TestUser(long id, String token) {}

  @Test
  @DisplayName("export contains exactly the caller's data and none of another user's")
  void exportIsScopedToCaller() throws Exception {
    TestUser a = seededUser("alpha");
    seededUser("bravo");

    Map<String, byte[]> zip = unzip(download(a, create(a).path("data").path("id").asText()));

    assertThat(zip)
        .containsKeys(
            "plants.json",
            "plants.csv",
            "care-log.json",
            "care-log.csv",
            "scan-history.json",
            "scan-history.csv",
            "journal.json",
            "journal.csv",
            "README.txt");
    assertThat(zip.keySet()).anyMatch(n -> n.startsWith("photos/plant-"));
    assertThat(zip.keySet()).anyMatch(n -> n.startsWith("photos/scan-"));

    JsonNode plants = objectMapper.readTree(zip.get("plants.json"));
    assertThat(plants).hasSize(2); // active + archived
    assertThat(plants.findValuesAsText("nickname"))
        .containsExactlyInAnyOrder("plant-alpha", "archived-alpha");
    assertThat(objectMapper.readTree(zip.get("care-log.json")).findValuesAsText("notes"))
        .containsExactly("care-alpha");
    assertThat(objectMapper.readTree(zip.get("scan-history.json")).findValuesAsText("userContext"))
        .containsExactly("scan-alpha");
    assertThat(objectMapper.readTree(zip.get("journal.json")).findValuesAsText("notes"))
        .containsExactly("journal-alpha");
    assertThat(text(zip.get("journal.csv"))).contains("journal-alpha");
    assertThat(text(zip.get("plants.csv"))).contains("plant-alpha").contains("archived-alpha");
    assertThat(text(zip.get("care-log.csv"))).contains("care-alpha");
    assertThat(text(zip.get("scan-history.csv"))).contains("scan-alpha");
    assertThat(photoContents(zip)).containsExactly("photo-alpha");

    // Nothing of B's (name, note, photo filename or photo content) appears in any entry.
    zip.forEach(
        (name, bytes) -> {
          assertThat(name).doesNotContain("bravo");
          assertThat(text(bytes)).doesNotContain("bravo");
        });
  }

  @Test
  @DisplayName("another user gets 404 for someone else's export id; unauthenticated gets 401")
  void downloadIsOwnerOnly() throws Exception {
    TestUser a = seededUser("charlie");
    TestUser b = seededUser("delta");
    String id = create(a).path("data").path("id").asText();

    assertThat(get(b, EXPORT_PATH + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(b, EXPORT_PATH + "/" + UUID.randomUUID()).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(b, EXPORT_PATH + "/not-a-uuid").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(a, EXPORT_PATH + "/" + id).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(restTemplate.getForEntity(EXPORT_PATH + "/" + id, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(restTemplate.postForEntity(EXPORT_PATH, null, String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("download is served as an attachment")
  void downloadIsAttachment() throws Exception {
    TestUser a = seededUser("echo");
    String id = create(a).path("data").path("id").asText();

    ResponseEntity<byte[]> response = get(a, EXPORT_PATH + "/" + id);

    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment");
  }

  @Test
  @DisplayName("the 4th export in the window returns 429 with Retry-After; others unaffected")
  void exportsAreRateLimited() throws Exception {
    TestUser a = seededUser("foxtrot");
    TestUser b = seededUser("golf");

    // JDK client: TestRestTemplate's Apache client would honour Retry-After and sleep for an hour.
    for (int i = 0; i < 3; i++) {
      assertThat(rawPost(a).statusCode()).isEqualTo(200);
    }
    java.net.http.HttpResponse<String> limited = rawPost(a);

    assertThat(limited.statusCode()).isEqualTo(429);
    assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    assertThat(rawPost(b).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("an expired export is refused, then deleted by cleanup")
  void expiredExportIsDeleted() throws Exception {
    TestUser a = seededUser("hotel");
    String id = create(a).path("data").path("id").asText();
    Path file = EXPORT_DIR.resolve(a.id() + "-" + id + ".zip");
    assertThat(file).exists();
    Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(Duration.ofHours(25))));

    assertThat(get(a, EXPORT_PATH + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    fileStore.deleteExpired();
    assertThat(file).doesNotExist();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static Path createExportDir() {
    try {
      return Files.createTempDirectory("plantpal-export-it");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private TestUser seededUser(String marker) throws IOException {
    Map<String, Object> body =
        Map.of(
            "email",
            marker + "-" + UUID.randomUUID() + "@example.com",
            "password",
            "password123",
            "firstName",
            "T",
            "lastName",
            "U");
    JsonNode data =
        restTemplate
            .postForEntity("/api/v1/auth/register", body, JsonNode.class)
            .getBody()
            .path("data");
    long id = data.path("userId").asLong();
    String photo = writePhoto(marker);

    Plant plant =
        plantRepository.save(
            Plant.builder()
                .userId(id)
                .nickname("plant-" + marker)
                .status(PlantStatus.ACTIVE)
                .photoUrl(photo)
                .build());
    plantRepository.save(
        Plant.builder()
            .userId(id)
            .nickname("archived-" + marker)
            .status(PlantStatus.ARCHIVED)
            .build());
    journalRepository.save(
        PlantJournalEvent.builder()
            .plantId(plant.getId())
            .userId(id)
            .type(JournalEventType.values()[0])
            .eventDate(java.time.LocalDate.of(2026, 1, 2))
            .note("journal-" + marker)
            .build());
    careLogRepository.save(
        CareLog.builder()
            .plantId(plant.getId())
            .userId(id)
            .careType(CareType.WATERING)
            .notes("care-" + marker)
            .build());
    identificationRepository.save(
        Identification.builder()
            .userId(id)
            .plantId(plant.getId())
            .dateTaken(Instant.now())
            .photoUrl(writePhoto(marker))
            .userContext("scan-" + marker)
            .status(IdentificationStatus.COMPLETED)
            .identificationStatus(IdentificationStageStatus.COMPLETED)
            .annotationStatus(IdentificationStageStatus.COMPLETED)
            .candidateStatus(IdentificationStageStatus.COMPLETED)
            .build());
    return new TestUser(id, data.path("token").asText());
  }

  private String writePhoto(String marker) throws IOException {
    String filename = UUID.randomUUID() + ".jpg";
    Path dir = Path.of(photoDir);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve(filename), "photo-" + marker);
    return "/photos/" + filename;
  }

  private HttpEntity<Void> auth(TestUser user) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(user.token());
    return new HttpEntity<>(headers);
  }

  private ResponseEntity<String> post(TestUser user) {
    return restTemplate.exchange(EXPORT_PATH, HttpMethod.POST, auth(user), String.class);
  }

  private java.net.http.HttpResponse<String> rawPost(TestUser user) throws Exception {
    java.net.http.HttpRequest request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl() + EXPORT_PATH))
            .header("Authorization", "Bearer " + user.token())
            .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
            .build();
    return java.net.http.HttpClient.newHttpClient()
        .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private ResponseEntity<byte[]> get(TestUser user, String path) {
    return restTemplate.exchange(path, HttpMethod.GET, auth(user), byte[].class);
  }

  private JsonNode create(TestUser user) throws IOException {
    ResponseEntity<String> response = post(user);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody());
  }

  private byte[] download(TestUser user, String id) {
    ResponseEntity<byte[]> response = get(user, EXPORT_PATH + "/" + id);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
    Map<String, byte[]> entries = new HashMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
        entries.put(e.getName(), in.readAllBytes());
      }
    }
    return entries;
  }

  private static List<String> photoContents(Map<String, byte[]> zip) {
    return zip.entrySet().stream()
        .filter(e -> e.getKey().startsWith("photos/"))
        .map(e -> text(e.getValue()))
        .distinct()
        .toList();
  }

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
