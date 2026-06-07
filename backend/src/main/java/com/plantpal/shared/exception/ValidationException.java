package com.plantpal.shared.exception;

public class ValidationException extends PlantPalException {

  private static final int ERROR_CODE = 400;

  public ValidationException(String message) {
    super(message, ERROR_CODE);
  }
}
