package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetImageUrls(String s, String m, String l, String o) {}
