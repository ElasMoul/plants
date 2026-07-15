package com.plantpal.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for rate limiting. Prod runs behind Railway's reverse proxy,
 * so {@code request.getRemoteAddr()} alone would return the proxy's own address for every request —
 * the real client is the first hop of {@code X-Forwarded-For} (the header is a comma-separated
 * list, client-first, each proxy appending its own address after that).
 */
public final class ClientIpResolver {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private ClientIpResolver() {}

  public static String resolve(HttpServletRequest request) {
    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      String firstHop = forwardedFor.split(",")[0].trim();
      if (!firstHop.isEmpty()) {
        return firstHop;
      }
    }
    return request.getRemoteAddr();
  }
}
