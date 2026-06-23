package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetIucnRef(String id, String category) {}
