package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.TreatmentPlanRequest;
import com.plantpal.reminder.dto.TreatmentPlanResponse;
import com.plantpal.reminder.service.TreatmentPlanService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/treatment-plans")
@Tag(name = "Treatment Plans", description = "Multi-step treatment plans generated from care cards")
@SecurityRequirement(name = "bearerAuth")
public class TreatmentPlanController {

  private final TreatmentPlanService treatmentPlanService;

  public TreatmentPlanController(TreatmentPlanService treatmentPlanService) {
    this.treatmentPlanService = treatmentPlanService;
  }

  @Operation(
      summary =
          "Create a treatment plan from a TREATMENT action plan, generating one"
              + " reminder per step")
  @PostMapping
  public ResponseEntity<ApiResponse<TreatmentPlanResponse>> createTreatmentPlan(
      @Valid @RequestBody TreatmentPlanRequest request) {
    Long userId = getCurrentUserId();
    TreatmentPlanResponse response =
        treatmentPlanService.createFromActionPlan(
            request.getPlantId(),
            userId,
            request.getTitle(),
            request.getSourceCareCardType(),
            request.getActionPlan());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Treatment plan created"));
  }

  @Operation(summary = "Get a treatment plan and its steps")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<TreatmentPlanResponse>> getTreatmentPlan(
      @PathVariable Long id) {
    Long userId = getCurrentUserId();
    TreatmentPlanResponse response = treatmentPlanService.getTreatmentPlan(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
