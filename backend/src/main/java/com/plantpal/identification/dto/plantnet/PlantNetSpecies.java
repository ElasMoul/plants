package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetSpecies(
    String scientificNameWithoutAuthor,
    List<String> commonNames,
    PlantNetTaxon genus,
    PlantNetTaxon family) {}
