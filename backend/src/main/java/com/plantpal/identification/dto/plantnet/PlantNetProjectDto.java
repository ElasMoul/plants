package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Minimal projection of a PlantNet /v2/projects entry — just what the frontend dropdown needs. The
 * PlantNet API returns many more fields; extras are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetProjectDto(
    String id, String name, Map<String, String> commonNames, List<String> languages) {}
