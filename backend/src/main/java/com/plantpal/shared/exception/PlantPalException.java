package com.plantpal.shared.exception;

import lombok.Getter;

@Getter
public class PlantPalException extends RuntimeException {

  private final int errorCode;

  public PlantPalException(String message, int errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  public PlantPalException(String message, int errorCode, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
