package com.plantpal.user.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.dto.VerifyEmailRequest;
import com.plantpal.user.entity.User;
import com.plantpal.user.service.EmailVerificationService;
import com.plantpal.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration and login — public, no bearer token required")
public class AuthController {

  private final UserService userService;
  private final EmailVerificationService emailVerification;

  public AuthController(UserService userService, EmailVerificationService emailVerification) {
    this.userService = userService;
    this.emailVerification = emailVerification;
  }

  @Operation(summary = "Register a new user account")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Registration successful"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Registration rate limit reached for this IP")
  })
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResponse response = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Registration successful"));
  }

  @Operation(summary = "Log in with email and password")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Login successful"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Validation error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Invalid email or password"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Login rate limit reached for this IP")
  })
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = userService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
  }

  @Operation(
      summary = "Verify an email address with the token from the emailed link",
      description =
          "Public. POST (not GET) so a mail scanner that prefetches links cannot spend the token.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Email verified"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Link unknown, altered, already used or expired")
  })
  @PostMapping("/verify-email")
  public ResponseEntity<ApiResponse<Void>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    emailVerification.verify(request.getToken());
    return ResponseEntity.ok(ApiResponse.success(null, "Email verified"));
  }

  @Operation(summary = "Send a new verification email to the signed-in user")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Sent, or already verified (nothing sent)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Too many verification emails requested")
  })
  @PostMapping("/verify-email/resend")
  public ResponseEntity<ApiResponse<Void>> resendVerification() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    emailVerification.resend(user.getId());
    return ResponseEntity.ok(ApiResponse.success(null, "Verification email sent"));
  }
}
