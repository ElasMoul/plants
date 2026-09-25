package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.plantnet.PlantNetImageUrls;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import com.plantpal.identification.dto.plantnet.PlantNetReferenceImage;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ValidationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("PlantNetClient — status mapping, catalog endpoints, helpers")
class PlantNetClientCatalogTest {

  private final List<MultipartFile> oneImage = List.of(image());
  private MockWebServer server;
  private PlantNetClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client = new PlantNetClient(server.url("/").toString(), "test-api-key", "all", 6, "en");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Nested
  @DisplayName("identify()")
  class Identify {

    @ParameterizedTest(name = "PlantNet {0} -> PlantPal {1}")
    @CsvSource({"400,400", "401,401", "413,413", "414,400", "415,415"})
    @DisplayName("maps PlantNet error statuses to PlantPal errors")
    void mapsErrorStatuses(int plantNetStatus, int plantPalCode) {
      server.enqueue(new MockResponse().setResponseCode(plantNetStatus));

      assertThatThrownBy(() -> client.identify(oneImage, null))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(plantPalCode));
    }

    @Test
    @DisplayName("429 is the daily quota rate limit")
    void quotaExhausted() {
      server.enqueue(new MockResponse().setResponseCode(429));

      assertThatThrownBy(() -> client.identify(oneImage, null))
          .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("an unmapped upstream status degrades to 503")
    void unmappedStatus() {
      server.enqueue(new MockResponse().setResponseCode(503));

      assertThatThrownBy(() -> client.identify(oneImage, null))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(503));
    }

    @Test
    @DisplayName("explicit project and lang are used, blank ones fall back to defaults")
    void projectAndLang() throws Exception {
      server.enqueue(json("{\"results\":[],\"remainingIdentificationRequests\":1}"));
      server.enqueue(json("{\"results\":[],\"remainingIdentificationRequests\":1}"));

      client.identify(List.of(image()), List.of("leaf"), "weurope", "fr");
      client.identify(List.of(image()), List.of("leaf"), " ", "");

      assertThat(server.takeRequest().getPath())
          .startsWith("/v2/identify/weurope?")
          .contains("lang=fr");
      assertThat(server.takeRequest().getPath())
          .startsWith("/v2/identify/all?")
          .contains("lang=en");
    }

    @Test
    @DisplayName("rejects bad image counts and oversize payloads before any request")
    void validation() {
      assertThatThrownBy(() -> client.identify(null, null)).isInstanceOf(ValidationException.class);
      List<MultipartFile> six = Collections.nCopies(6, image());
      assertThatThrownBy(() -> client.identify(six, null)).isInstanceOf(ValidationException.class);
      MultipartFile huge =
          new MockMultipartFile("images", "big.jpg", "image/jpeg", new byte[1]) {
            @Override
            public long getSize() {
              return 51L * 1024 * 1024;
            }
          };
      List<MultipartFile> hugeOnly = List.of(huge);
      assertThatThrownBy(() -> client.identify(hugeOnly, null))
          .isInstanceOf(ValidationException.class);
      assertThat(server.getRequestCount()).isZero();
    }
  }

  @Nested
  @DisplayName("catalog endpoints")
  class Catalog {

    @Test
    @DisplayName("getProjects passes lat/lon only when both are present")
    void projectsWithAndWithoutLocation() throws Exception {
      String body = "[{\"id\":\"weurope\",\"name\":\"Western Europe\"}]";
      server.enqueue(json(body));
      server.enqueue(json(body));
      server.enqueue(json(body));

      List<PlantNetProjectDto> projects = client.getProjects(48.8, 2.3, "fr");
      client.getProjects(48.8, null, null);
      client.getProjects(null, null, " ");

      assertThat(projects).extracting(PlantNetProjectDto::id).containsExactly("weurope");
      assertThat(server.takeRequest().getPath())
          .contains("lang=fr")
          .contains("lat=48.8")
          .contains("lon=2.3");
      assertThat(server.takeRequest().getPath()).contains("lang=en").doesNotContain("lat=");
      assertThat(server.takeRequest().getPath()).contains("lang=en").doesNotContain("lat=");
    }

    @Test
    @DisplayName("the projects cache key separates locations and languages")
    void projectsCacheKeyIncludesLocation() throws NoSuchMethodException {
      Cacheable cacheable =
          PlantNetClient.class
              .getMethod("getProjects", Double.class, Double.class, String.class)
              .getAnnotation(Cacheable.class);

      assertThat(cacheable.key()).contains("#lang").contains("#lat").contains("#lon");
    }

    @Test
    @DisplayName("getProjects and getLanguages map HTTP errors to 502 and outages to 503")
    void catalogFailures() throws IOException {
      server.enqueue(new MockResponse().setResponseCode(500));
      server.enqueue(new MockResponse().setResponseCode(500));

      assertThatThrownBy(() -> client.getProjects(null, null, "en"))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(502));
      assertThatThrownBy(() -> client.getLanguages())
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(502));

      server.shutdown();
      assertThatThrownBy(() -> client.getProjects(null, null, "en"))
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(503));
      assertThatThrownBy(() -> client.getLanguages())
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(503));
    }

    @Test
    @DisplayName("getLanguages returns plain codes")
    void languages() {
      server.enqueue(json("[\"en\",\"fr\"]"));

      assertThat(client.getLanguages()).containsExactly("en", "fr");
    }

    @Test
    @DisplayName("getQuota parses the quota, falls back to -1/-1 when unreachable, 502 on error")
    void quota() throws IOException {
      server.enqueue(json("{\"remaining\":480,\"total\":500}"));
      server.enqueue(new MockResponse().setResponseCode(401));

      PlantNetQuotaDto quota = client.getQuota();
      assertThat(quota.remaining()).isEqualTo(480);
      assertThat(quota.total()).isEqualTo(500);
      assertThatThrownBy(() -> client.getQuota())
          .isInstanceOfSatisfying(
              PlantPalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(502));

      server.shutdown();
      PlantNetQuotaDto fallback = client.getQuota();
      assertThat(fallback.remaining()).isEqualTo(-1);
      assertThat(fallback.total()).isEqualTo(-1);
    }
  }

  @Test
  @DisplayName("safeReferenceImages keeps at most 3 images that have a small or medium URL")
  void safeReferenceImages() {
    PlantNetReferenceImage usable =
        new PlantNetReferenceImage(new PlantNetImageUrls("s", null, null, null), "a", "l", "c");
    PlantNetReferenceImage mediumOnly =
        new PlantNetReferenceImage(new PlantNetImageUrls(null, "m", null, null), "a", "l", "c");
    PlantNetReferenceImage largeOnly =
        new PlantNetReferenceImage(new PlantNetImageUrls(null, null, "l", "o"), "a", "l", "c");
    PlantNetReferenceImage noUrls = new PlantNetReferenceImage(null, "a", "l", "c");

    List<PlantNetReferenceImage> images =
        Arrays.asList(null, largeOnly, noUrls, usable, mediumOnly, usable, usable);

    assertThat(PlantNetClient.safeReferenceImages(result(images)))
        .containsExactly(usable, mediumOnly, usable);
    assertThat(PlantNetClient.safeReferenceImages(result(null))).isEmpty();
  }

  private static PlantNetResult result(List<PlantNetReferenceImage> images) {
    return new PlantNetResult(0.9, null, null, null, null, images);
  }

  private static MockMultipartFile image() {
    return new MockMultipartFile(
        "images", "plant.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  private static MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
