package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A single language entry from PlantNet /v2/languages. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetLanguageDto(String code, String label) {}
