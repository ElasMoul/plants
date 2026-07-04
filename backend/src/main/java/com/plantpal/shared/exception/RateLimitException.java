package com.plantpal.shared.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends PlantPalException {

  private final Long retryAfterSeconds;

  public RateLimitException(String message, Long retryAfterSeconds) {
    super(message, 429);
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
