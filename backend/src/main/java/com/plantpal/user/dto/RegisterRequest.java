package com.plantpal.user.dto;

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
  private String email;

  @NotBlank
  @Size(min = 8, message = "Password must be at least 8 characters")
  private String password;

  @NotBlank
  private String firstName;

  @NotBlank
  private String lastName;
}
