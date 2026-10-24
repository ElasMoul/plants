package com.plantpal.localization;

import java.util.List;
import java.util.Locale;

/** The one backend definition of the languages the app supports: English (default) and French. */
public final class SupportedLanguages {

  public static final String DEFAULT = "en";
  public static final List<String> CODES = List.of("en", "fr");

  /** Bean Validation regex for request DTOs; kept next to {@link #CODES}. */
  public static final String PATTERN = "en|fr";

  private SupportedLanguages() {}

  public static boolean isSupported(String code) {
    return code != null && CODES.contains(code);
  }

  /** Returns a supported code, or English for null, blank and unsupported values (e.g. "ar"). */
  public static String normalize(String code) {
    if (code == null) return DEFAULT;
    String lower = code.trim().toLowerCase(Locale.ROOT);
    return CODES.contains(lower) ? lower : DEFAULT;
  }

  /** The language name used in "respond in ..." prompt instructions. */
  public static String displayName(String code) {
    return "fr".equals(normalize(code)) ? "French" : "English";
  }
}
