package com.plantpal.mail;

/** The transport could not hand the message to the provider; the dispatcher will retry. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
