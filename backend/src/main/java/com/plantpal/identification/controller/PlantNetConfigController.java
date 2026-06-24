package com.plantpal.identification.controller;

import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated proxy for PlantNet metadata endpoints (projects + languages). Results are cached
 * 24h server-side via @Cacheable on PlantNetClient — the frontend never burns identify quota on
 * these calls.
 */
@RestController
@RequestMapping("/api/v1/plantnet")
@Tag(name = "PlantNet Config", description = "Proxy for PlantNet flora and language metadata")
@SecurityRequirement(name = "bearerAuth")
public class PlantNetConfigController {

  private final PlantNetClient plantNetClient;

  public PlantNetConfigController(PlantNetClient plantNetClient) {
    this.plantNetClient = plantNetClient;
  }

  @Operation(summary = "List available PlantNet floras (projects), optionally ranked by location")
  @GetMapping("/projects")
  public ResponseEntity<ApiResponse<List<PlantNetProjectDto>>> getProjects(
      @RequestParam @Nullable Double lat,
      @RequestParam @Nullable Double lon,
      @RequestParam(defaultValue = "en") String lang) {
    List<PlantNetProjectDto> projects = plantNetClient.getProjects(lat, lon, lang);
    return ResponseEntity.ok(ApiResponse.success(projects));
  }

  @Operation(summary = "List all language codes supported by PlantNet common-name responses")
  @GetMapping("/languages")
  public ResponseEntity<ApiResponse<List<String>>> getLanguages() {
    List<String> languages = plantNetClient.getLanguages();
    return ResponseEntity.ok(ApiResponse.success(languages));
  }
}
