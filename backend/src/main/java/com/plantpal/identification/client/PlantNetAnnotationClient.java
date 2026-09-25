package com.plantpal.identification.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PlantNetAnnotationClient implements VisionAnnotationClient {

  private static final Logger log = LoggerFactory.getLogger(PlantNetAnnotationClient.class);
  private static final String EMPTY_REGIONS = "{\"regions\":[]}";

  private final PlantNetClient plantNetClient;
  private final ObjectMapper objectMapper;

  public PlantNetAnnotationClient(PlantNetClient plantNetClient, ObjectMapper objectMapper) {
    this.plantNetClient = plantNetClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public String analyzeRegions(byte[] imageBytes, String mediaType) {
    try {
      MultipartFile file = new PlantNetDiseaseClient.ByteArrayMultipartFile(imageBytes, mediaType);
      PlantNetResponse response = plantNetClient.identify(List.of(file), List.of("auto"));
      return toAnnotationJson(response);
    } catch (Exception e) {
      log.warn("PlantNet annotation unavailable, returning empty regions: {}", e.getMessage());
      return EMPTY_REGIONS;
    }
  }

  private String toAnnotationJson(PlantNetResponse response) {
    if (response == null || response.results() == null || response.results().isEmpty()) {
      return EMPTY_REGIONS;
    }
    List<Map<String, Object>> regions = new ArrayList<>();
    for (PlantNetResult result : response.results()) {
      // Skip unusable results individually — Map.of() rejects a null label, and that exception
      // would otherwise reach analyzeRegions()' catch-all and discard every region.
      if (result.species() == null || isBlank(result.species().scientificNameWithoutAuthor())) {
        continue;
      }
      String label = result.species().scientificNameWithoutAuthor();
      List<String> commonNames = result.species().commonNames();
      if (commonNames != null && !commonNames.isEmpty()) {
        label += " (" + commonNames.get(0) + ")";
      }
      String confidence = result.score() >= 0.7 ? "HIGH" : result.score() >= 0.4 ? "MEDIUM" : "LOW";
      regions.add(
          Map.of(
              "label",
              label,
              "type",
              "PLANT",
              "confidence",
              confidence,
              "boundingBox",
              Map.of("xPct", 0, "yPct", 0, "widthPct", 100, "heightPct", 100)));
    }
    try {
      return objectMapper.writeValueAsString(Map.of("regions", regions));
    } catch (JsonProcessingException e) {
      return EMPTY_REGIONS;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
