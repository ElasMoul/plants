package com.plantpal.user.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Parses a stored IANA time zone id; null or unparseable means UTC. */
public final class UserZones {

  private UserZones() {}

  public static ZoneId of(String id) {
    if (id == null || id.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(id);
    } catch (DateTimeException e) {
      return ZoneOffset.UTC;
    }
  }
}
