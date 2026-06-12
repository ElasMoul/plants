package com.plantpal.user.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.dto.AuthResponse;
import com.plantpal.user.dto.LoginRequest;
import com.plantpal.user.dto.RegisterRequest;
import com.plantpal.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    AuthResponse response = userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Registration successful"));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = userService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
  }
}
