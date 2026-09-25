package com.plantpal.identification.client;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads an upstream 429's wait time: the numeric {@code Retry-After} header first, then (when a
 * body pattern is given) a "wait N seconds" hint in the error body, else the fallback. Negative,
 * non-numeric (e.g. HTTP-date) and overflowing values are ignored rather than surfaced.
 */
final class RetryAfterSeconds {

  static final Pattern WAIT_SECONDS_IN_BODY = Pattern.compile("wait (\\d+) seconds?");

  private RetryAfterSeconds() {}

  static long from(RestClientResponseException e, Pattern bodyPattern, long fallbackSeconds) {
    HttpHeaders headers = e.getResponseHeaders();
    String header = headers != null ? headers.getFirst("Retry-After") : null;
    OptionalLong fromHeader = parseNonNegative(header);
    if (fromHeader.isPresent()) {
      return fromHeader.getAsLong();
    }
    if (bodyPattern != null) {
      Matcher matcher = bodyPattern.matcher(String.valueOf(e.getResponseBodyAsString()));
      if (matcher.find()) {
        OptionalLong fromBody = parseNonNegative(matcher.group(1));
        if (fromBody.isPresent()) {
          return fromBody.getAsLong();
        }
      }
    }
    return fallbackSeconds;
  }

  private static OptionalLong parseNonNegative(String value) {
    if (value == null) {
      return OptionalLong.empty();
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds >= 0 ? OptionalLong.of(seconds) : OptionalLong.empty();
    } catch (NumberFormatException ignored) {
      return OptionalLong.empty();
    }
  }
}
