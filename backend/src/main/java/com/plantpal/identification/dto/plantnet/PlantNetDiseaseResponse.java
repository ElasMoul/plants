package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Response from PlantNet {@code /v2/diseases/identify}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetDiseaseResponse(
    List<PlantNetDiseaseResult> results, int remainingIdentificationRequests) {}
