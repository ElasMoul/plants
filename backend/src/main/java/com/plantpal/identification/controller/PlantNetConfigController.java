package com.plantpal.identification.controller;

import com.plantpal.gateway.GatewayProperties;
import com.plantpal.gateway.PlantNetGatewayClient;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.plantnet.PlantNetProjectDto;
import com.plantpal.identification.dto.plantnet.PlantNetQuotaDto;
import com.plantpal.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 * Authenticated proxy for PlantNet metadata endpoints (projects + languages + quota). Direct-path
 * results are cached 24h server-side via @Cacheable on PlantNetClient — the frontend never burns
 * identify quota on these calls.
 *
 * <p>Gap G4 follow-up (ai-gateway full-coverage demand): when {@code platform.gateway.enabled} is
 * on, these three lookups route through ai-gateway's {@code /ai/plantnet/*} endpoints instead (D022
 * — PlantNet's API key never leaves the gateway); same additive if/else shape used throughout the
 * identification module for the gateway swap. The direct {@link PlantNetClient} path — including
 * its caching — remains unchanged for standalone/dev (D009).
 */
@RestController
@RequestMapping("/api/v1/plantnet")
@Tag(name = "PlantNet Config", description = "Proxy for PlantNet flora and language metadata")
@SecurityRequirement(name = "bearerAuth")
public class PlantNetConfigController {

  private final PlantNetClient plantNetClient;
  private final PlantNetGatewayClient plantNetGatewayClient;
  private final GatewayProperties gatewayProperties;

  public PlantNetConfigController(
      PlantNetClient plantNetClient,
      PlantNetGatewayClient plantNetGatewayClient,
      GatewayProperties gatewayProperties) {
    this.plantNetClient = plantNetClient;
    this.plantNetGatewayClient = plantNetGatewayClient;
    this.gatewayProperties = gatewayProperties;
  }

  @Operation(summary = "List available PlantNet floras (projects), optionally ranked by location")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Projects retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/projects")
  public ResponseEntity<ApiResponse<List<PlantNetProjectDto>>> getProjects(
      @Parameter(description = "Latitude, for location-ranked results") @RequestParam @Nullable
          Double lat,
      @Parameter(description = "Longitude, for location-ranked results") @RequestParam @Nullable
          Double lon,
      @Parameter(description = "Language code for common names") @RequestParam(defaultValue = "en")
          String lang) {
    List<PlantNetProjectDto> projects =
        gatewayProperties.enabled()
            ? plantNetGatewayClient.getProjects(lat, lon, lang)
            : plantNetClient.getProjects(lat, lon, lang);
    return ResponseEntity.ok(ApiResponse.success(projects));
  }

  @Operation(summary = "List all language codes supported by PlantNet common-name responses")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Languages retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/languages")
  public ResponseEntity<ApiResponse<List<String>>> getLanguages() {
    List<String> languages =
        gatewayProperties.enabled()
            ? plantNetGatewayClient.getLanguages()
            : plantNetClient.getLanguages();
    return ResponseEntity.ok(ApiResponse.success(languages));
  }

  @Operation(summary = "Fetch today's remaining Pl@ntNet identify quota (cached 5 min)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Quota retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/quota")
  public ResponseEntity<ApiResponse<PlantNetQuotaDto>> getQuota() {
    PlantNetQuotaDto quota =
        gatewayProperties.enabled() ? plantNetGatewayClient.getQuota() : plantNetClient.getQuota();
    return ResponseEntity.ok(ApiResponse.success(quota));
  }
}
