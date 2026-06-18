package com.plantpal.reminder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PushSubscriptionRequest {

  @NotBlank private String endpoint;

  @NotBlank private String keyP256dh;

  @NotBlank private String keyAuth;
}
