package com.plantpal.export.service;

import java.util.List;

/** Minimal RFC 4180 CSV writer that also neutralises spreadsheet formula injection. */
public final class CsvEscaper {

  private static final String FORMULA_PREFIXES = "=+-@\t\r";

  private CsvEscaper() {}

  public static String escape(Object value) {
    if (value == null) {
      return "";
    }
    String text = String.valueOf(value);
    boolean neutralised = false;
    // Genuine numbers (e.g. a negative value) are data, not formulas.
    if (!(value instanceof Number)
        && !text.isEmpty()
        && FORMULA_PREFIXES.indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
      neutralised = true;
    }
    boolean quote =
        neutralised
            || text.indexOf(',') >= 0
            || text.indexOf('"') >= 0
            || text.indexOf('\n') >= 0
            || text.indexOf('\r') >= 0;
    return quote ? '"' + text.replace("\"", "\"\"") + '"' : text;
  }

  public static String row(List<?> values) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        line.append(',');
      }
      line.append(escape(values.get(i)));
    }
    return line.append("\r\n").toString();
  }
}
