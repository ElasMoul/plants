package com.plantpal.shared.exception;

public class ResourceNotFoundException extends PlantPalException {

  private static final int ERROR_CODE = 404;

  public ResourceNotFoundException(String resource, Long id) {
    super(resource + " not found with id: " + id, ERROR_CODE);
  }

  public ResourceNotFoundException(String message) {
    super(message, ERROR_CODE);
  }
}
