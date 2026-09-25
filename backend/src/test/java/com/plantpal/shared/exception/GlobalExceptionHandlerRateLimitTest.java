package com.plantpal.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.shared.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("GlobalExceptionHandler — rate limit responses")
class GlobalExceptionHandlerRateLimitTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("a known wait is sent as Retry-After and in the body")
  void knownWait() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleRateLimit(new RateLimitException("slow down", 30L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    assertThat(response.getBody().getRetryAfterSeconds()).isEqualTo(30L);
  }

  @Test
  @DisplayName("an unknown wait omits the header instead of sending \"null\"")
  void unknownWait() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleRateLimit(new RateLimitException("slow down", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().containsKey("Retry-After")).isFalse();
  }
}
