package com.plantpal.identification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class DeepSeekAnnotationClient implements VisionAnnotationClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekAnnotationClient.class);
  private static final String EMPTY_REGIONS = "{\"regions\":[]}";

  private final DeepSeekClient deepSeekClient;

  public DeepSeekAnnotationClient(DeepSeekClient deepSeekClient) {
    this.deepSeekClient = deepSeekClient;
  }

  @Override
  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    // Two attempts: the first may fail with EOF if the Azure HTTP/2 connection
    // is closed by a GOAWAY after the parallel identifyPlant() call completes.
    // A retry establishes a fresh connection and succeeds.
    Exception lastException = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        return deepSeekClient.analyzeRegions(imageBytes, mediaType);
      } catch (Exception e) {
        lastException = e;
        if (attempt < 2) {
          log.debug(
              "DeepSeek annotation attempt {} failed ({}), retrying", attempt, e.getMessage());
        }
      }
    }
    log.warn(
        "DeepSeek annotation unavailable, returning empty regions: {}",
        lastException != null ? lastException.getMessage() : "unknown");
    return EMPTY_REGIONS;
  }
}
