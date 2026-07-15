package com.plantpal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

  @NotBlank
  @Email
  @Schema(example = "jane@example.com")
  private String email;

  @NotBlank
  @Schema(example = "correcthorsebattery")
  private String password;
}
