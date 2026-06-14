package com.plantpal.identification.client;

import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ValidationException;
import java.io.IOException;
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

@Component
public class PlantNetClient {

  private static final Logger log = LoggerFactory.getLogger(PlantNetClient.class);

  private static final int MAX_IMAGES = 5;

  private final RestClient restClient;
  private final String apiKey;
  private final String project;

  public PlantNetClient(
      @Value("${app.plantnet.base-url:https://my-api.plantnet.org}") String baseUrl,
      @Value("${app.plantnet.api-key}") String apiKey,
      @Value("${app.plantnet.project:all}") String project) {
    this.apiKey = apiKey;
    this.project = project;
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

  public PlantNetResponse identify(List<MultipartFile> images, List<String> organs) {
    if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
      throw new ValidationException("Between 1 and " + MAX_IMAGES + " images are required");
    }

    long start = System.currentTimeMillis();
    try {
      MultiValueMap<String, Object> body = buildMultipartBody(images, organs);

      PlantNetResponse response =
          restClient
              .post()
              .uri("/v2/identify/{project}?api-key={key}", project, apiKey)
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
                        "No species match found for the provided image", 404);
                  })
              .onStatus(
                  status -> status.value() == 413,
                  (req, res) -> {
                    throw new PlantPalException("Image file is too large for PlantNet", 413);
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
                    throw new PlantPalException(
                        "PlantNet rate limit reached — please try again later", 429);
                  })
              .onStatus(
                  status -> status.value() == 500,
                  (req, res) -> {
                    throw new PlantPalException(
                        "PlantNet service encountered an internal error", 502);
                  })
              .body(PlantNetResponse.class);

      log.info("PlantNet identification completed in {}ms", System.currentTimeMillis() - start);
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
}
