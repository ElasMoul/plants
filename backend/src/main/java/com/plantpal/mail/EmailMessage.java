package com.plantpal.mail;

/** A fully rendered email, ready for a transport. */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {}
