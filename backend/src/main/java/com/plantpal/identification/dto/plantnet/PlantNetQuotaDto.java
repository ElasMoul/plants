package com.plantpal.identification.dto.plantnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Daily quota snapshot returned by PlantNet {@code GET /v2/quota/daily}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlantNetQuotaDto(int remaining, int total) {}
