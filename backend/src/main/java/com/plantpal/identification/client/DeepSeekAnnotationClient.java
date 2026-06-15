package com.plantpal.identification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Primary
@Component
public class DeepSeekAnnotationClient implements VisionAnnotationClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekAnnotationClient.class);
  private static final String EMPTY_REGIONS = "{\"regions\":[]}";

  private final DeepSeekClient deepSeekClient;
  private final OllamaClient ollamaClient;

  public DeepSeekAnnotationClient(DeepSeekClient deepSeekClient, OllamaClient ollamaClient) {
    this.deepSeekClient = deepSeekClient;
    this.ollamaClient = ollamaClient;
  }

  @Override
  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    // Two attempts: the first may fail with EOF if the Azure HTTP/2 connection
    // is closed by a GOAWAY after the parallel identifyPlant() call completes.
    // A retry establishes a fresh connection and succeeds.
    // 429 (rate limit) is detected immediately and routed to Ollama instead of retrying.
    Exception lastException = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        return deepSeekClient.analyzeRegions(imageBytes, mediaType);
      } catch (RestClientResponseException e) {
        if (e.getStatusCode().value() == 429) {
          log.warn("DeepSeek annotation rate-limited (429), falling back to Ollama");
          return tryOllamaFallback(imageBytes, mediaType);
        }
        lastException = e;
        if (attempt < 2) {
          log.debug(
              "DeepSeek annotation attempt {} failed ({}), retrying", attempt, e.getMessage());
        }
      } catch (Exception e) {
        lastException = e;
        if (attempt < 2) {
          log.debug(
              "DeepSeek annotation attempt {} failed ({}), retrying", attempt, e.getMessage());
        }
      }
    }
    log.warn(
        "DeepSeek annotation unavailable after retries, returning empty regions: {}",
        lastException != null ? lastException.getMessage() : "unknown");
    return EMPTY_REGIONS;
  }

  private String tryOllamaFallback(byte[] imageBytes, String mediaType) {
    try {
      return ollamaClient.analyzeRegions(imageBytes, mediaType);
    } catch (Exception e) {
      log.warn(
          "Ollama annotation fallback also failed ({}), returning empty regions", e.getMessage());
      return EMPTY_REGIONS;
    }
  }
}
