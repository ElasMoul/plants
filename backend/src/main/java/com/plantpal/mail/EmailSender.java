package com.plantpal.mail;

/**
 * Port to the outside world: one rendered message in, delivered to the provider or an {@link
 * EmailDeliveryException} out. Nothing else in the application talks SMTP.
 */
public interface EmailSender {

  void send(EmailMessage message);
}
