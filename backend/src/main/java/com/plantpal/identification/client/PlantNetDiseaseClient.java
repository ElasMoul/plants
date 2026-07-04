package com.plantpal.identification.client;

import com.plantpal.identification.dto.plantnet.PlantNetDiseaseResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

/**
 * PlantNet disease/pest cross-check client (T8.5). Sends POST /v2/diseases/identify
 * (cultivated-plant scoped) as a second opinion alongside gpt-4o annotation. A 404 or empty result
 * means "no corroboration", not an error — callers must treat it that way. HTTP/1.1 forced for ALPN
 * parity with {@link PlantNetClient}.
 */
@Component
public class PlantNetDiseaseClient {

  private static final Logger log = LoggerFactory.getLogger(PlantNetDiseaseClient.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

  private final RestClient restClient;
  private final String apiKey;
  private final int nbResults;
  private final String defaultLang;

  public PlantNetDiseaseClient(
      @Value("${app.plantnet.base-url:https://my-api.plantnet.org}") String baseUrl,
      @Value("${app.plantnet.api-key}") String apiKey,
      @Value("${app.plantnet.nb-results:6}") int nbResults,
      @Value("${app.plantnet.lang:en}") String defaultLang) {
    this.apiKey = apiKey;
    this.nbResults = nbResults;
    this.defaultLang = defaultLang;
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
   * Identify diseases/pests on the given images. Returns an empty response (no results) when
   * PlantNet returns 404 (non-plant or no match) — callers must not treat empty as an error. A 429
   * is propagated as {@link RateLimitException} so the existing rate-limit UX handles it.
   */
  public PlantNetDiseaseResponse identifyDisease(
      List<MultipartFile> images, List<String> organs, String lang) {
    if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
      throw new ValidationException("Between 1 and " + MAX_IMAGES + " images are required");
    }
    long totalBytes = images.stream().mapToLong(MultipartFile::getSize).sum();
    if (totalBytes > MAX_TOTAL_BYTES) {
      throw new ValidationException("Total image payload must not exceed 50 MB");
    }
    String effectiveLang = (lang != null && !lang.isBlank()) ? lang : defaultLang;

    long start = System.currentTimeMillis();
    try {
      MultiValueMap<String, Object> body = buildMultipartBody(images, organs);

      PlantNetDiseaseResponse response =
          restClient
              .post()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v2/diseases/identify")
                          .queryParam("api-key", apiKey)
                          .queryParam("include-related-images", "true")
                          .queryParam("nb-results", nbResults)
                          .queryParam("lang", effectiveLang)
                          .build())
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .onStatus(
                  status -> status.value() == 404,
                  (req, res) -> {
                    throw new PlantPalException("plantnet-disease-no-match", 404);
                  })
              .onStatus(
                  status -> status.value() == 429,
                  (req, res) -> {
                    throw new RateLimitException(
                        "PlantNet disease quota exhausted — try again tomorrow", 0L);
                  })
              .onStatus(
                  status -> status.isError(),
                  (req, res) -> {
                    throw new PlantPalException(
                        "PlantNet disease check failed: " + res.getStatusCode(), 502);
                  })
              .body(PlantNetDiseaseResponse.class);

      log.info(
          "PlantNet disease check completed in {}ms, quota_remaining={}",
          System.currentTimeMillis() - start,
          response != null ? response.remainingIdentificationRequests() : "?");
      return response != null ? response : new PlantNetDiseaseResponse(List.of(), 0);

    } catch (PlantPalException e) {
      if (e.getMessage().equals("plantnet-disease-no-match")) {
        log.debug("PlantNet disease: no match (404) — treating as no corroboration");
        return new PlantNetDiseaseResponse(List.of(), 0);
      }
      throw e;
    } catch (RestClientException e) {
      log.warn(
          "PlantNet disease check unreachable, continuing without cross-check: {}", e.getMessage());
      return new PlantNetDiseaseResponse(List.of(), 0);
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

  /** Adapts raw bytes to a {@link MultipartFile} for this client. */
  public static final class ByteArrayMultipartFile implements MultipartFile {
    private final byte[] bytes;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] bytes, String contentType) {
      this.bytes = bytes;
      this.contentType = contentType;
    }

    @Override
    public String getName() {
      return "image";
    }

    @Override
    public String getOriginalFilename() {
      return "image.jpg";
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return bytes.length == 0;
    }

    @Override
    public long getSize() {
      return bytes.length;
    }

    @Override
    public byte[] getBytes() {
      return bytes;
    }

    @Override
    public InputStream getInputStream() {
      return new java.io.ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(java.io.File dest) {
      throw new UnsupportedOperationException();
    }
  }
}
