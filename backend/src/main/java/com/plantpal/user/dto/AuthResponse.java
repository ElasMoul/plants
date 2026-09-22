package com.plantpal.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

  private String token;

  private String role;

  @Builder.Default private String tokenType = "Bearer";

  private long expiresIn;

  private Long userId;

  private String email;

  private String firstName;
}
