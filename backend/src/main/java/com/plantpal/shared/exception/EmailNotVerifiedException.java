package com.plantpal.shared.exception;

/** Thrown when an action that needs a verified email address is attempted without one. */
public class EmailNotVerifiedException extends PlantPalException {

  public static final String CODE = "EMAIL_NOT_VERIFIED";

  private static final int ERROR_CODE = 403;

  public EmailNotVerifiedException() {
    super("Verify your email address to use this feature", ERROR_CODE);
  }
}
