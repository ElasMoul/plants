package com.plantpal.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Getter;
import org.slf4j.MDC;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final String message;
  private final Integer errorCode;
  private final Long retryAfterSeconds;
  private final String code;
  private final String correlationId;
  private final Instant timestamp;

  private ApiResponse(
      boolean success,
      T data,
      String message,
      Integer errorCode,
      Long retryAfterSeconds,
      String code) {
    this.success = success;
    this.data = data;
    this.message = message;
    this.errorCode = errorCode;
    this.retryAfterSeconds = retryAfterSeconds;
    this.code = code;
    this.correlationId = MDC.get("correlationId");
    this.timestamp = Instant.now();
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, null, null, null);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, null, message, null, null, null);
  }

  public static <T> ApiResponse<T> error(String message, int errorCode) {
    return new ApiResponse<>(false, null, message, errorCode, null, null);
  }

  public static <T> ApiResponse<T> error(String message, int errorCode, Long retryAfterSeconds) {
    return new ApiResponse<>(false, null, message, errorCode, retryAfterSeconds, null);
  }

  /** An error the client can branch on: {@code code} is a stable machine-readable key. */
  public static <T> ApiResponse<T> error(String message, int errorCode, String code) {
    return new ApiResponse<>(false, null, message, errorCode, null, code);
  }
}
