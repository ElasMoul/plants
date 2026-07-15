package com.plantpal.reminder.controller;

import com.plantpal.reminder.dto.PushSubscriptionRequest;
import com.plantpal.reminder.service.WebPushService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Web push notification subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

  private final WebPushService webPushService;

  public NotificationController(WebPushService webPushService) {
    this.webPushService = webPushService;
  }

  @Operation(summary = "Register a browser push subscription for the current user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Push subscription registered"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized")
  })
  @PostMapping("/subscribe")
  public ResponseEntity<ApiResponse<Void>> subscribe(
      @Valid @RequestBody PushSubscriptionRequest request) {
    Long userId = getCurrentUserId();
    webPushService.saveSubscription(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(null, "Push subscription registered"));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
