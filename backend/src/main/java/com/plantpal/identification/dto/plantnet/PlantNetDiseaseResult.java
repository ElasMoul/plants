package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A single disease/pest result from PlantNet {@code /v2/diseases/identify}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetDiseaseResult(
    String name,
    String description,
    double score,
    String eppo,
    List<PlantNetReferenceImage> images) {}
