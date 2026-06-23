package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetReferenceImage(
    PlantNetImageUrls url, String author, String license, String citation) {}
