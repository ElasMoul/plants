package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetResponse(
    List<PlantNetResult> results,
    String bestMatch,
    String switchToProject,
    List<PlantNetPredictedOrgan> predictedOrgans,
    String version,
    int remainingIdentificationRequests) {}
