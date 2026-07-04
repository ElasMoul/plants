package com.plantpal.user.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.dto.UserPreferencesRequest;
import com.plantpal.user.dto.UserPreferencesResponse;
import com.plantpal.user.entity.User;
import com.plantpal.user.service.UserService;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile and preferences")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "Get current user AI model preference")
  @GetMapping("/me/preferences")
  public ResponseEntity<ApiResponse<UserPreferencesResponse>> getPreferences() {
    Long userId = getCurrentUserId();
    UserPreferencesResponse response = userService.getPreferences(userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Update current user AI model preference")
  @PutMapping("/me/preferences")
  public ResponseEntity<ApiResponse<UserPreferencesResponse>> updatePreferences(
      @Valid @RequestBody UserPreferencesRequest request) {
    Long userId = getCurrentUserId();
    UserPreferencesResponse response = userService.updatePreferences(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Preferences updated successfully"));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
