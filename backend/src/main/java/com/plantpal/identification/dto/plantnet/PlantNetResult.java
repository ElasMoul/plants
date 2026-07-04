package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetResult(
    double score,
    PlantNetSpecies species,
    PlantNetGbifRef gbif,
    PlantNetPowoRef powo,
    PlantNetIucnRef iucn,
    List<PlantNetReferenceImage> images) {}
