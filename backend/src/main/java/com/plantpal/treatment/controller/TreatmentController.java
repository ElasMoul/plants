package com.plantpal.treatment.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import com.plantpal.treatment.service.TreatmentService;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Treatments", description = "Per-disease treatment lifecycle for a plant")
@SecurityRequirement(name = "bearerAuth")
public class TreatmentController {

  private final TreatmentService treatmentService;

  public TreatmentController(TreatmentService treatmentService) {
    this.treatmentService = treatmentService;
  }

  @Operation(summary = "Start a DRAFT treatment for a detected disease on a plant")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Treatment started"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error (e.g. an active treatment already exists)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Plant not found")
  })
  @PostMapping("/treatments")
  public ResponseEntity<ApiResponse<TreatmentResponse>> createTreatment(
      @Valid @RequestBody CreateTreatmentRequest request) {
    TreatmentResponse response = treatmentService.createTreatment(request, getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Treatment started"));
  }

  @Operation(summary = "Generate the treatment's action plan and start tracking reminders")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Treatment plan crafted"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Treatment or plant not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Treatment AI rate limit reached")
  })
  @PostMapping("/treatments/{id}/craft-plan")
  public ResponseEntity<ApiResponse<TreatmentResponse>> craftPlan(
      @Parameter(description = "Treatment ID") @PathVariable Long id) {
    Long userId = getCurrentUserId();
    try {
      TreatmentResponse response = treatmentService.craftPlan(id, userId).get();
      return ResponseEntity.ok(ApiResponse.success(response, "Treatment plan crafted"));
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof PlantPalException ppe) throw ppe;
      throw new PlantPalException("Treatment plan crafting failed", 500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PlantPalException("Treatment plan crafting was interrupted", 500);
    }
  }

  @Operation(summary = "Get a single treatment by ID")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Treatment retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Treatment not found")
  })
  @GetMapping("/treatments/{id}")
  public ResponseEntity<ApiResponse<TreatmentResponse>> getTreatment(
      @Parameter(description = "Treatment ID") @PathVariable Long id) {
    TreatmentResponse response = treatmentService.getTreatment(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Get the active (DRAFT/IN_PROGRESS) treatment for a plant, if any")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Active treatment retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "No active treatment for this plant")
  })
  @GetMapping("/plants/{id}/active-treatment")
  public ResponseEntity<ApiResponse<TreatmentResponse>> getActiveTreatment(
      @Parameter(description = "Plant ID") @PathVariable Long id) {
    TreatmentResponse response =
        treatmentService.getActiveTreatmentForPlant(id, getCurrentUserId());
    if (response == null) {
      throw new ResourceNotFoundException("No active treatment for this plant");
    }
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Get every active (DRAFT/IN_PROGRESS) treatment for a plant")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Active treatments retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/plants/{id}/active-treatments")
  public ResponseEntity<ApiResponse<List<TreatmentResponse>>> getActiveTreatments(
      @Parameter(description = "Plant ID") @PathVariable Long id) {
    List<TreatmentResponse> response =
        treatmentService.getActiveTreatmentsForPlant(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Mark an IN_PROGRESS treatment as completed")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Treatment completed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Only an IN_PROGRESS treatment can be completed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Treatment not found")
  })
  @PatchMapping("/treatments/{id}/complete")
  public ResponseEntity<ApiResponse<TreatmentResponse>> completeTreatment(
      @Parameter(description = "Treatment ID") @PathVariable Long id) {
    TreatmentResponse response = treatmentService.completeTreatment(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Treatment completed"));
  }

  @Operation(summary = "Re-fire async disease description generation (T9.B)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "202",
        description = "Regeneration queued"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Treatment not found")
  })
  @PostMapping("/treatments/{id}/regenerate-description")
  public ResponseEntity<ApiResponse<TreatmentResponse>> regenerateDescription(
      @Parameter(description = "Treatment ID") @PathVariable Long id) {
    TreatmentResponse response = treatmentService.regenerateDescription(id, getCurrentUserId());
    return ResponseEntity.accepted().body(ApiResponse.success(response));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
