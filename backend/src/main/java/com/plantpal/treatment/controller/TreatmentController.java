package com.plantpal.treatment.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.treatment.dto.CreateTreatmentRequest;
import com.plantpal.treatment.dto.TreatmentResponse;
import com.plantpal.treatment.service.TreatmentService;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
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
  @PostMapping("/treatments")
  public ResponseEntity<ApiResponse<TreatmentResponse>> createTreatment(
      @Valid @RequestBody CreateTreatmentRequest request) {
    TreatmentResponse response = treatmentService.createTreatment(request, getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Treatment started"));
  }

  @Operation(summary = "Generate the treatment's action plan and start tracking reminders")
  @PostMapping("/treatments/{id}/craft-plan")
  public ResponseEntity<ApiResponse<TreatmentResponse>> craftPlan(@PathVariable Long id) {
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
  @GetMapping("/treatments/{id}")
  public ResponseEntity<ApiResponse<TreatmentResponse>> getTreatment(@PathVariable Long id) {
    TreatmentResponse response = treatmentService.getTreatment(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Get the active (DRAFT/IN_PROGRESS) treatment for a plant, if any")
  @GetMapping("/plants/{id}/active-treatment")
  public ResponseEntity<ApiResponse<TreatmentResponse>> getActiveTreatment(@PathVariable Long id) {
    TreatmentResponse response =
        treatmentService.getActiveTreatmentForPlant(id, getCurrentUserId());
    if (response == null) {
      throw new ResourceNotFoundException("No active treatment for this plant");
    }
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Get every active (DRAFT/IN_PROGRESS) treatment for a plant")
  @GetMapping("/plants/{id}/active-treatments")
  public ResponseEntity<ApiResponse<List<TreatmentResponse>>> getActiveTreatments(
      @PathVariable Long id) {
    List<TreatmentResponse> response =
        treatmentService.getActiveTreatmentsForPlant(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Mark an IN_PROGRESS treatment as completed")
  @PatchMapping("/treatments/{id}/complete")
  public ResponseEntity<ApiResponse<TreatmentResponse>> completeTreatment(@PathVariable Long id) {
    TreatmentResponse response = treatmentService.completeTreatment(id, getCurrentUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Treatment completed"));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
