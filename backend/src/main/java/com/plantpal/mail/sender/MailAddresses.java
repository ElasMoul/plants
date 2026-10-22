package com.plantpal.mail.sender;

/** Keeps full addresses out of logs. */
public final class MailAddresses {

  private MailAddresses() {}

  public static String mask(String address) {
    if (address == null) {
      return "";
    }
    int at = address.indexOf('@');
    return at <= 0 ? "***" : address.charAt(0) + "***" + address.substring(at);
  }
}
