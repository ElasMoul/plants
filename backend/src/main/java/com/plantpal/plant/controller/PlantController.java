package com.plantpal.plant.controller;

import com.plantpal.plant.dto.CreatePlantRequest;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.service.PlantService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plants")
@Tag(name = "Plants", description = "Plant collection management")
@SecurityRequirement(name = "bearerAuth")
public class PlantController {

  private static final Logger log = LoggerFactory.getLogger(PlantController.class);

  private final PlantService plantService;

  public PlantController(PlantService plantService) {
    this.plantService = plantService;
  }

  @Operation(summary = "List all active plants for the authenticated user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Plants retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping
  public ResponseEntity<ApiResponse<Page<PlantResponse>>> getUserPlants(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long userId = getCurrentUserId();
    Page<PlantResponse> plants = plantService.getUserPlants(userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(plants));
  }

  @Operation(summary = "Add a new plant to the collection")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Plant created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @PostMapping
  public ResponseEntity<ApiResponse<PlantResponse>> createPlant(
      @Valid @RequestBody CreatePlantRequest request) {
    Long userId = getCurrentUserId();
    PlantResponse response = plantService.createPlant(request, userId);
    log.info("Plant created via API: id={}, userId={}", response.getId(), userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Plant added successfully"));
  }

  @Operation(summary = "Get a single plant by ID")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Plant retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Plant not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PlantResponse>> getPlant(@PathVariable Long id) {
    PlantResponse response = plantService.getPlant(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Update plant details")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Plant updated successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Plant not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PlantResponse>> updatePlant(
      @PathVariable Long id, @Valid @RequestBody UpdatePlantRequest request) {
    PlantResponse response = plantService.updatePlant(id, request, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Plant updated successfully"));
  }

  @Operation(summary = "Archive a plant (soft delete — data is preserved)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Plant archived successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Plant not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archivePlant(@PathVariable Long id) {
    plantService.archivePlant(id, getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
