package com.plantpal.identification.client;

public interface VisionAnnotationClient {

  /**
   * Analyse the image and return a JSON string of the form {@code {"regions":[...]}}.
   *
   * <p>Implementations must NEVER throw — return {@code {"regions":[]}} on any error.
   */
  String analyzeRegions(byte[] imageBytes, String mediaType);
}
