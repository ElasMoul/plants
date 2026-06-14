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
    try {
      return deepSeekClient.analyzeRegions(imageBytes, mediaType);
    } catch (Exception e) {
      log.warn("DeepSeek annotation unavailable, returning empty regions: {}", e.getMessage());
      return EMPTY_REGIONS;
    }
  }
}
