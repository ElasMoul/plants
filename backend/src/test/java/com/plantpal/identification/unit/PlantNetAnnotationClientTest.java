package com.plantpal.identification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.PlantNetAnnotationClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.dto.plantnet.PlantNetSpecies;
import com.plantpal.shared.exception.PlantPalException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlantNetAnnotationClient — Unit Tests")
class PlantNetAnnotationClientTest {

  private static final String EMPTY_REGIONS = "{\"regions\":[]}";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private PlantNetClient plantNetClient;

  private PlantNetAnnotationClient client;

  @BeforeEach
  void setUp() {
    client = new PlantNetAnnotationClient(plantNetClient, objectMapper);
  }

  @Test
  @DisplayName("maps each species result to a whole-image PLANT region with banded confidence")
  void mapsResultsToRegions() throws Exception {
    givenResults(
        result(0.85, "Monstera deliciosa", List.of("Swiss cheese plant")),
        result(0.5, "Philodendron hederaceum", List.of()),
        result(0.1, "Epipremnum aureum", null));

    JsonNode regions = regions(client.analyzeRegions(new byte[] {1}, "image/jpeg"));

    assertThat(regions).hasSize(3);
    assertThat(regions.get(0).path("label").asText())
        .isEqualTo("Monstera deliciosa (Swiss cheese plant)");
    assertThat(regions.get(0).path("confidence").asText()).isEqualTo("HIGH");
    assertThat(regions.get(0).path("type").asText()).isEqualTo("PLANT");
    assertThat(regions.get(0).path("boundingBox").path("widthPct").asInt()).isEqualTo(100);
    assertThat(regions.get(1).path("label").asText()).isEqualTo("Philodendron hederaceum");
    assertThat(regions.get(1).path("confidence").asText()).isEqualTo("MEDIUM");
    assertThat(regions.get(2).path("confidence").asText()).isEqualTo("LOW");
  }

  @Test
  @DisplayName("skips a nameless or species-less result without dropping the others")
  void skipsUnusableResultsOnly() throws Exception {
    givenResults(
        new PlantNetResult(0.9, null, null, null, null, null),
        result(0.9, null, List.of()),
        result(0.8, "Ficus lyrata", List.of("Fiddle-leaf fig")));

    JsonNode regions = regions(client.analyzeRegions(new byte[] {1}, "image/jpeg"));

    assertThat(regions).hasSize(1);
    assertThat(regions.get(0).path("label").asText()).isEqualTo("Ficus lyrata (Fiddle-leaf fig)");
  }

  @Test
  @DisplayName("returns empty regions for a null or empty response")
  void emptyResponses() {
    when(plantNetClient.identify(anyList(), eq(List.of("auto")))).thenReturn(null);
    assertThat(client.analyzeRegions(new byte[] {1}, "image/jpeg")).isEqualTo(EMPTY_REGIONS);

    givenResults();
    assertThat(client.analyzeRegions(new byte[] {1}, "image/jpeg")).isEqualTo(EMPTY_REGIONS);
  }

  @Test
  @DisplayName("a PlantNet failure degrades to empty regions")
  void failureIsEmpty() {
    when(plantNetClient.identify(anyList(), eq(List.of("auto"))))
        .thenThrow(new PlantPalException("PlantNet down", 502));

    assertThat(client.analyzeRegions(new byte[] {1}, "image/jpeg")).isEqualTo(EMPTY_REGIONS);
  }

  private void givenResults(PlantNetResult... results) {
    when(plantNetClient.identify(anyList(), eq(List.of("auto"))))
        .thenReturn(new PlantNetResponse(List.of(results), null, null, null, null, 100));
  }

  private static PlantNetResult result(double score, String name, List<String> commonNames) {
    return new PlantNetResult(
        score, new PlantNetSpecies(name, commonNames, null, null), null, null, null, null);
  }

  private JsonNode regions(String json) throws Exception {
    return objectMapper.readTree(json).path("regions");
  }
}
