package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminders")
@Tag(name = "Reminders", description = "Plant care reminders")
@SecurityRequirement(name = "bearerAuth")
public class ReminderController {

  private final ReminderService reminderService;

  public ReminderController(ReminderService reminderService) {
    this.reminderService = reminderService;
  }

  @Operation(summary = "Create a new care reminder")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Reminder created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @PostMapping
  public ResponseEntity<ApiResponse<ReminderResponse>> createReminder(
      @Valid @RequestBody CreateReminderRequest request) {
    Long userId = getCurrentUserId();
    ReminderResponse response = reminderService.createReminder(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Reminder created successfully"));
  }

  @Operation(summary = "List enabled reminders for the current user, sorted by next due date")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Reminders retrieved successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @GetMapping
  public ResponseEntity<ApiResponse<List<ReminderResponse>>> getUserReminders() {
    Long userId = getCurrentUserId();
    List<ReminderResponse> reminders = reminderService.getUserReminders(userId);
    return ResponseEntity.ok(ApiResponse.success(reminders));
  }

  @Operation(summary = "Mark a reminder as done — logs the care action and reschedules it")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Reminder completed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Reminder not found")
  })
  @PostMapping("/{id}/complete")
  public ResponseEntity<ApiResponse<ReminderResponse>> completeReminder(
      @Parameter(description = "Reminder ID") @PathVariable Long id) {
    Long userId = getCurrentUserId();
    ReminderResponse response = reminderService.completeReminder(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Reminder completed"));
  }

  @Operation(summary = "Disable a reminder (soft delete)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Reminder disabled successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Reminder not found")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteReminder(
      @Parameter(description = "Reminder ID") @PathVariable Long id) {
    reminderService.deleteReminder(id, getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
