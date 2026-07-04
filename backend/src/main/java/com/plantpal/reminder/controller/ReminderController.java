package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.CreateReminderRequest;
import com.plantpal.reminder.dto.ReminderResponse;
import com.plantpal.reminder.service.ReminderService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
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
  @PostMapping
  public ResponseEntity<ApiResponse<ReminderResponse>> createReminder(
      @Valid @RequestBody CreateReminderRequest request) {
    Long userId = getCurrentUserId();
    ReminderResponse response = reminderService.createReminder(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Reminder created successfully"));
  }

  @Operation(summary = "List enabled reminders for the current user, sorted by next due date")
  @GetMapping
  public ResponseEntity<ApiResponse<List<ReminderResponse>>> getUserReminders() {
    Long userId = getCurrentUserId();
    List<ReminderResponse> reminders = reminderService.getUserReminders(userId);
    return ResponseEntity.ok(ApiResponse.success(reminders));
  }

  @Operation(summary = "Mark a reminder as done — logs the care action and reschedules it")
  @PostMapping("/{id}/complete")
  public ResponseEntity<ApiResponse<ReminderResponse>> completeReminder(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    ReminderResponse response = reminderService.completeReminder(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Reminder completed"));
  }

  @Operation(summary = "Disable a reminder (soft delete)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
    reminderService.deleteReminder(id, getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
