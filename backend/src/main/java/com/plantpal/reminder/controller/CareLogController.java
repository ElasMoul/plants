package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.CareLogResponse;
import com.plantpal.reminder.dto.MarkCareDoneRequest;
import com.plantpal.reminder.service.CareLogService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/care")
@Tag(name = "Care Logs", description = "Recording completed plant care actions")
@SecurityRequirement(name = "bearerAuth")
public class CareLogController {

  private final CareLogService careLogService;

  public CareLogController(CareLogService careLogService) {
    this.careLogService = careLogService;
  }

  @Operation(summary = "Mark a reminder as done — logs the care action and reschedules it")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Care logged successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Reminder not found")
  })
  @PostMapping("/done")
  public ResponseEntity<ApiResponse<CareLogResponse>> markCareDone(
      @Valid @RequestBody MarkCareDoneRequest request) {
    Long userId = getCurrentUserId();
    CareLogResponse response = careLogService.logCare(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Care logged successfully"));
  }

  @Operation(summary = "Get the care history for a plant")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Care logs retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping("/plant/{plantId}")
  public ResponseEntity<ApiResponse<Page<CareLogResponse>>> getPlantCareLogs(
      @Parameter(description = "Plant ID") @PathVariable Long plantId,
      @PageableDefault(size = 20, sort = "performedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long userId = getCurrentUserId();
    Page<CareLogResponse> logs = careLogService.getPlantCareLogs(plantId, userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(logs));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
