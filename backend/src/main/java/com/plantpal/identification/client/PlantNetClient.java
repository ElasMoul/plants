package com.plantpal.identification.client;

import com.plantpal.identification.dto.plantnet.PlantNetImageUrls;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import com.plantpal.identification.dto.plantnet.PlantNetReferenceImage;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ValidationException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/**
 * PlantNet v2 client. Sends multipart POST /v2/identify/{project} with api-key as a query parameter
 * (NOT a header — the PlantNet API requires it on the URL). Always sets type=kt (legacy format
 * token required by the API). HTTP/1.1 forced to work around ALPN EOF on large multipart bodies;
 * Content-Length (not chunked) avoids Broken pipe.
 */
@Component
public class PlantNetClient {

  private static final Logger log = LoggerFactory.getLogger(PlantNetClient.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
  private static final int MAX_REFERENCE_IMAGES = 3;

  private final RestClient restClient;
  private final String apiKey;
  private final String defaultProject;
  private final int nbResults;
  private final String defaultLang;

  public PlantNetClient(
      @Value("${app.plantnet.base-url:https://my-api.plantnet.org}") String baseUrl,
      @Value("${app.plantnet.api-key}") String apiKey,
      @Value("${app.plantnet.project:all}") String defaultProject,
      @Value("${app.plantnet.nb-results:6}") int nbResults,
      @Value("${app.plantnet.lang:en}") String defaultLang) {
    this.apiKey = apiKey;
    this.defaultProject = defaultProject;
    this.nbResults = nbResults;
    this.defaultLang = defaultLang;
    // Force HTTP/1.1: PlantNet drops HTTP/2 connections on large multipart bodies (ALPN EOF).
    // JDK HTTP/1.1 also sends with Content-Length (no chunked encoding), avoiding Broken pipe
    // that Apache HC5 triggers when PlantNet rejects Transfer-Encoding: chunked multipart.
    HttpClient http1Client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http1Client);
    factory.setReadTimeout(Duration.ofSeconds(120));
    this.restClient = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
  }

  /**
   * Identify plant(s) via PlantNet v2. Uses the configured default project and language. Pass
   * organs per image for best accuracy (defaults to "auto" when null/empty).
   */
  public PlantNetResponse identify(List<MultipartFile> images, List<String> organs) {
    return identify(images, organs, defaultProject, defaultLang);
  }

  /**
   * Identify plant(s) via PlantNet v2 with explicit project and language. Project and lang are
   * user-preference driven (T8.4); for T8.1 the caller typically uses the defaults.
   */
  public PlantNetResponse identify(
      List<MultipartFile> images, List<String> organs, String project, String lang) {
    if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
      throw new ValidationException("Between 1 and " + MAX_IMAGES + " images are required");
    }

    long totalBytes = images.stream().mapToLong(MultipartFile::getSize).sum();
    if (totalBytes > MAX_TOTAL_BYTES) {
      throw new ValidationException("Total image payload must not exceed 50 MB");
    }

    String effectiveProject = (project != null && !project.isBlank()) ? project : defaultProject;
    String effectiveLang = (lang != null && !lang.isBlank()) ? lang : defaultLang;

    long start = System.currentTimeMillis();
    try {
      MultiValueMap<String, Object> body = buildMultipartBody(images, organs);

      PlantNetResponse response =
          restClient
              .post()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v2/identify/{project}")
                          .queryParam("api-key", apiKey)
                          .queryParam("type", "kt")
                          .queryParam("include-related-images", "true")
                          .queryParam("no-reject", "false")
                          .queryParam("nb-results", nbResults)
                          .queryParam("lang", effectiveLang)
                          .build(effectiveProject))
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .onStatus(
                  status -> status.value() == 400,
                  (req, res) -> {
                    throw new PlantPalException("Invalid request sent to PlantNet", 400);
                  })
              .onStatus(
                  status -> status.value() == 401,
                  (req, res) -> {
                    throw new PlantPalException("PlantNet API key is invalid or missing", 401);
                  })
              .onStatus(
                  status -> status.value() == 404,
                  (req, res) -> {
                    throw new PlantPalException(
                        "No plant species match found — this image does not appear to be a plant",
                        404);
                  })
              .onStatus(
                  status -> status.value() == 413,
                  (req, res) -> {
                    throw new PlantPalException(
                        "Image payload is too large for PlantNet (max 50 MB)", 413);
                  })
              .onStatus(
                  status -> status.value() == 414,
                  (req, res) -> {
                    throw new PlantPalException("PlantNet request URI is too long", 400);
                  })
              .onStatus(
                  status -> status.value() == 415,
                  (req, res) -> {
                    throw new PlantPalException("Unsupported image format — use JPEG or PNG", 415);
                  })
              .onStatus(
                  status -> status.value() == 429,
                  (req, res) -> {
                    throw new RateLimitException(
                        "PlantNet daily quota exhausted — try again tomorrow", 0L);
                  })
              .onStatus(
                  status -> status.value() == 500,
                  (req, res) -> {
                    throw new PlantPalException(
                        "PlantNet service encountered an internal error", 502);
                  })
              .body(PlantNetResponse.class);

      log.info(
          "PlantNet identification completed in {}ms, project={}, quota_remaining={}",
          System.currentTimeMillis() - start,
          effectiveProject,
          response != null ? response.remainingIdentificationRequests() : "?");
      return response;

    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach PlantNet API", e);
      throw new PlantPalException("PlantNet service unavailable", 503);
    }
  }

  private MultiValueMap<String, Object> buildMultipartBody(
      List<MultipartFile> images, List<String> organs) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

    for (MultipartFile image : images) {
      try {
        byte[] bytes = image.getBytes();
        String filename =
            image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.jpg";
        ByteArrayResource resource =
            new ByteArrayResource(bytes) {
              @Override
              public String getFilename() {
                return filename;
              }
            };
        body.add("images", resource);
      } catch (IOException e) {
        throw new PlantPalException("Failed to read image data", 400);
      }
    }

    List<String> effectiveOrgans = (organs != null && !organs.isEmpty()) ? organs : List.of("auto");
    for (String organ : effectiveOrgans) {
      body.add("organs", organ);
    }

    return body;
  }

  /**
   * Fetch available PlantNet floras (projects) for the given language. Results are cached 24h — the
   * project list changes rarely. Pass lat/lon to get a location-ranked list; omit both for the
   * global, unranked list (ship without geo first per T8.4 task prompt).
   */
  @Cacheable(value = "plantnet-projects", key = "#lang")
  public List<PlantNetProjectDto> getProjects(
      @Nullable Double lat, @Nullable Double lon, String lang) {
    try {
      List<PlantNetProjectDto> projects =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    var builder =
                        uriBuilder
                            .path("/v2/projects")
                            .queryParam("api-key", apiKey)
                            .queryParam("type", "kt")
                            .queryParam("lang", lang != null && !lang.isBlank() ? lang : "en");
                    if (lat != null && lon != null) {
                      builder = builder.queryParam("lat", lat).queryParam("lon", lon);
                    }
                    return builder.build();
                  })
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (req, res) -> {
                    throw new PlantPalException(
                        "Failed to fetch PlantNet project list: " + res.getStatusCode(), 502);
                  })
              .body(new ParameterizedTypeReference<List<PlantNetProjectDto>>() {});
      return projects != null ? projects : List.of();
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach PlantNet /v2/projects", e);
      throw new PlantPalException("PlantNet service unavailable", 503);
    }
  }

  /**
   * Fetch all PlantNet supported language codes. Results are cached 24h. The API returns a plain
   * {@code List<String>} (language codes like "en", "fr") — not objects.
   */
  @Cacheable("plantnet-languages")
  public List<String> getLanguages() {
    try {
      List<String> langs =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder.path("/v2/languages").queryParam("api-key", apiKey).build())
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (req, res) -> {
                    throw new PlantPalException(
                        "Failed to fetch PlantNet language list: " + res.getStatusCode(), 502);
                  })
              .body(new ParameterizedTypeReference<List<String>>() {});
      return langs != null ? langs : List.of();
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.error("Failed to reach PlantNet /v2/languages", e);
      throw new PlantPalException("PlantNet service unavailable", 503);
    }
  }

  /**
   * Fetch today's remaining identify quota. Cached 5 min (short TTL so the display stays reasonably
   * fresh). Returns a fallback with -1/-1 when the endpoint is unreachable so the UI can show
   * "unavailable" rather than breaking.
   */
  @Cacheable("plantnet-quota")
  public PlantNetQuotaDto getQuota() {
    try {
      PlantNetQuotaDto quota =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder.path("/v2/quota/daily").queryParam("api-key", apiKey).build())
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (req, res) -> {
                    throw new PlantPalException(
                        "Failed to fetch PlantNet quota: " + res.getStatusCode(), 502);
                  })
              .body(PlantNetQuotaDto.class);
      return quota != null ? quota : new PlantNetQuotaDto(-1, -1);
    } catch (PlantPalException e) {
      throw e;
    } catch (RestClientException e) {
      log.warn("Failed to reach PlantNet /v2/quota/daily: {}", e.getMessage());
      return new PlantNetQuotaDto(-1, -1);
    }
  }

  /** Cap reference image list and discard entries with no usable URL. */
  public static List<PlantNetReferenceImage> safeReferenceImages(PlantNetResult result) {
    if (result.images() == null) return List.of();
    return result.images().stream()
        .filter(img -> img != null && hasUsableUrl(img.url()))
        .limit(MAX_REFERENCE_IMAGES)
        .toList();
  }

  private static boolean hasUsableUrl(PlantNetImageUrls urls) {
    if (urls == null) return false;
    return urls.s() != null || urls.m() != null;
  }
}
