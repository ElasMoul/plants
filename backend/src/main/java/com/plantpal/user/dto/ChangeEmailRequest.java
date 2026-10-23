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
public class ChangeEmailRequest {

  @NotBlank
  @Email
  @Size(max = 255)
  private String newEmail;

  @NotBlank(message = "Current password is required")
  private String currentPassword;
}
