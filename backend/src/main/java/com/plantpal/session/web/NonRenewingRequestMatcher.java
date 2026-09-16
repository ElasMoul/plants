package com.plantpal.session.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * D3's non-renewing allowlist: authenticated requests that must NOT slide the idle window,
 * enumerated explicitly rather than inferred from request shape (an inferred rule fails
 * silently — a 30-minute session quietly becoming unbounded is exactly the risk the
 * architecture pack calls out).
 *
 * <p>GET /api/v1/auth/session is the one entry with certainty behind it (apiSketch: "explicitly
 * NON-renewing... otherwise polling it would slide the window forever"). D3 also names
 * background polling, telemetry, health traffic and SSE keep-alives as categories that must not
 * renew — health and telemetry already never reach here (unauthenticated / platform-profile-only
 * adapters), but which *domain* polling endpoints (if any) belong on this list is an open
 * question this wave doesn't have verified evidence to answer yet. Extend this matcher, with a
 * test per entry, once that's determined — do not infer it.
 */
@Component
public class NonRenewingRequestMatcher {

  private final RequestMatcher matcher =
      new OrRequestMatcher(
          new AntPathRequestMatcher("/api/v1/auth/session", HttpMethod.GET.name()));

  public boolean isNonRenewing(HttpServletRequest request) {
    return matcher.matches(request);
  }
}
