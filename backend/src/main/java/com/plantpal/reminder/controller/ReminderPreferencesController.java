package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.ReminderPreferencesRequest;
import com.plantpal.reminder.dto.ReminderPreferencesResponse;
import com.plantpal.reminder.service.ReminderPreferencesService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@Tag(name = "Notifications", description = "Reminder channel and quiet hours")
@SecurityRequirement(name = "bearerAuth")
public class ReminderPreferencesController {

  private final ReminderPreferencesService preferencesService;

  public ReminderPreferencesController(ReminderPreferencesService preferencesService) {
    this.preferencesService = preferencesService;
  }

  @Operation(summary = "Get the current user's reminder channel and quiet hours")
  @GetMapping
  public ResponseEntity<ApiResponse<ReminderPreferencesResponse>> get() {
    return ResponseEntity.ok(ApiResponse.success(preferencesService.get(currentUserId())));
  }

  @Operation(summary = "Save the current user's reminder channel and quiet hours")
  @PutMapping
  public ResponseEntity<ApiResponse<ReminderPreferencesResponse>> update(
      @Valid @RequestBody ReminderPreferencesRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            preferencesService.update(currentUserId(), request), "Reminder preferences saved"));
  }

  private Long currentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
