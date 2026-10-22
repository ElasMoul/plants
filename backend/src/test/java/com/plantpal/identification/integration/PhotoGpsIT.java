package com.plantpal.identification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.migration.PhotoGpsCleaner;
import com.plantpal.identification.migration.StripPhotoGpsTask;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.util.PhotoGpsStripper;
import com.plantpal.shared.storage.FileStorageService;
import com.plantpal.testdata.GpsPhotoFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@DisplayName("Photo GPS removal (PLA-118) — upload, serve, migrate")
class PhotoGpsIT extends AbstractIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private IdentificationRepository identificationRepository;
  @Autowired private FileStorageService fileStorageService;
  @Autowired private PhotoGpsCleaner cleaner;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private javax.sql.DataSource dataSource;

  @Value("${app.storage.local-path:/tmp/plantpal/photos}")
  private String localPath;

  @MockBean private KafkaTemplate<String, Object> kafkaTemplate;

  private String token;

  @BeforeEach
  void setUp() {
    token = registerAndLogin();
  }

  @Test
  @DisplayName("upload stores no GPS, keeps date_taken, and every read path serves clean bytes")
  void uploadStoresAndServesWithoutGps() throws Exception {
    byte[] original = GpsPhotoFixtures.gpsJpeg();
    assertThat(GpsPhotoFixtures.hasGps(original)).isTrue();

    ResponseEntity<Map> response = upload(original);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
    Identification saved =
        identificationRepository
            .findById(((Number) data.get("identificationId")).longValue())
            .orElseThrow();
    assertThat(saved.getDateTaken()).isEqualTo(Instant.parse(GpsPhotoFixtures.DATE_TAKEN_ISO));

    String url = saved.getPhotoUrl();
    String filename = url.substring(url.lastIndexOf('/') + 1);
    // disk copy
    byte[] onDisk = Files.readAllBytes(Paths.get(localPath).resolve(filename));
    assertThat(GpsPhotoFixtures.hasGps(onDisk)).isFalse();
    // Redis-cached read (what the AI pipeline and PlantNet receive via loadPhotoBytes)
    assertThat(GpsPhotoFixtures.hasGps(fileStorageService.loadPhotoBytes(url))).isFalse();
    // served: authenticated, and anonymous, on both URL shapes
    for (String path : List.of("/photos/" + filename, "/api/v1/photos/" + filename)) {
      assertThat(GpsPhotoFixtures.hasGps(get(path, token))).as(path + " (auth)").isFalse();
      assertThat(GpsPhotoFixtures.hasGps(get(path, null))).as(path + " (anon)").isFalse();
    }
    assertThat(onDisk).hasSizeLessThanOrEqualTo(original.length);
  }

  @Test
  @DisplayName("an upload whose metadata cannot be cleaned is rejected, not stored")
  void corruptUploadIsRejected() {
    ResponseEntity<Map> response = upload("definitely not a jpeg".getBytes());

    assertThat(response.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName(
      "migration 042: cleans GPS photos in storage and cache, leaves others, is idempotent")
  void migrationCleansStoredPhotos() throws Exception {
    String gpsUrl = fileStorageService.savePhoto(jpeg("gps.jpg", GpsPhotoFixtures.gpsJpeg()));
    byte[] plain = GpsPhotoFixtures.plainJpeg();
    String plainUrl = fileStorageService.savePhoto(jpeg("plain.jpg", plain));
    String corruptUrl = fileStorageService.savePhoto(jpeg("bad.jpg", "garbage".getBytes()));
    long userId = jdbc.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
    for (String url : List.of(gpsUrl, plainUrl, corruptUrl)) {
      jdbc.update(
          "INSERT INTO identifications(user_id,status,photo_url,date_taken) "
              + "VALUES (?,'COMPLETED',?,now())",
          userId,
          url);
    }
    assertThat(GpsPhotoFixtures.hasGps(fileStorageService.loadPhotoBytes(gpsUrl))).isTrue();

    PhotoGpsCleaner.Summary first;
    PhotoGpsCleaner.Summary second;
    try (var connection = dataSource.getConnection()) {
      first = invokeTask(connection, cleaner);
      byte[] afterFirst = fileStorageService.loadPhotoBytes(gpsUrl);
      second = invokeTask(connection, cleaner);
      assertThat(fileStorageService.loadPhotoBytes(gpsUrl)).isEqualTo(afterFirst);
    }

    assertThat(first.rewritten()).isEqualTo(1);
    assertThat(first.failed()).isGreaterThanOrEqualTo(1);
    assertThat(second.rewritten()).isZero();
    byte[] cached = fileStorageService.loadPhotoBytes(gpsUrl);
    assertThat(GpsPhotoFixtures.hasGps(cached)).isFalse();
    assertThat(PhotoGpsStripper.hasGps(cached)).isFalse();
    String filename = gpsUrl.substring(gpsUrl.lastIndexOf('/') + 1);
    assertThat(GpsPhotoFixtures.hasGps(Files.readAllBytes(Path.of(localPath, filename)))).isFalse();
    assertThat(fileStorageService.loadPhotoBytes(plainUrl)).isEqualTo(plain);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM databasechangelog WHERE id = '042-strip-photo-gps'",
                Integer.class))
        .isEqualTo(1);
  }

  private PhotoGpsCleaner.Summary invokeTask(java.sql.Connection connection, PhotoGpsCleaner c)
      throws Exception {
    return StripPhotoGpsTask.run(connection, c);
  }

  private MockMultipartFile jpeg(String name, byte[] bytes) {
    return new MockMultipartFile("image", name, "image/jpeg", bytes);
  }

  private ResponseEntity<Map> upload(byte[] bytes) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "images",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "photo.jpg";
          }
        });
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return restTemplate.exchange(
        url("/api/v1/identifications/analyze"),
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        Map.class);
  }

  private byte[] get(String path, String bearer) {
    HttpHeaders headers = new HttpHeaders();
    if (bearer != null) {
      headers.setBearerAuth(bearer);
    }
    ResponseEntity<byte[]> response =
        restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  @SuppressWarnings("unchecked")
  private String registerAndLogin() {
    var request =
        Map.of(
            "email", "gps-" + UUID.randomUUID() + "@example.com",
            "password", "password123",
            "firstName", "Gps",
            "lastName", "Test");
    ResponseEntity<Map> response =
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, Map.class);
    return (String) ((Map<String, Object>) response.getBody().get("data")).get("token");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
