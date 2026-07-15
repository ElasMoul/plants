package com.plantpal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

  @NotBlank
  @Email
  @Schema(example = "jane@example.com")
  private String email;

  @NotBlank
  @Size(min = 8, message = "Password must be at least 8 characters")
  @Schema(example = "correcthorsebattery")
  private String password;

  @NotBlank
  @Schema(example = "Jane")
  private String firstName;

  @NotBlank
  @Schema(example = "Doe")
  private String lastName;
}
