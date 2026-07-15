package com.plantpal.shared.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AuthRateLimitFilter")
class AuthRateLimitFilterTest {

  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final String REGISTER_PATH = "/api/v1/auth/register";
  private static final String OTHER_PATH = "/api/v1/plants";

  private FilterChain filterChain;

  @BeforeEach
  void setUp() {
    filterChain = mock(FilterChain.class);
  }

  /**
   * ApiResponse.timestamp is an {@link java.time.Instant} — the real app's autoconfigured
   * ObjectMapper bean registers JavaTimeModule automatically (Spring Boot), but a bare {@code new
   * ObjectMapper()} in a unit test does not, so it's done explicitly here.
   */
  private ObjectMapper testObjectMapper() {
    return new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
  }

  private HttpServletRequest requestFor(String method, String path, String remoteAddr) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getRequestURI()).thenReturn(path);
    when(request.getRemoteAddr()).thenReturn(remoteAddr);
    return request;
  }

  private StringWriter capturingWriter(HttpServletResponse response) throws Exception {
    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));
    return sw;
  }

  @Nested
  @DisplayName("login path (app.rate-limit.auth-attempts-per-minute)")
  class LoginPath {

    @Test
    @DisplayName("allows requests up to the configured limit, then 429s with Retry-After")
    void allowsUpToLimitThenRateLimits() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 3, 100);
      HttpServletResponse response = mock(HttpServletResponse.class);
      capturingWriter(response);

      for (int i = 0; i < 3; i++) {
        HttpServletRequest request = requestFor("POST", LOGIN_PATH, "203.0.113.9");
        filter.doFilter(request, response, filterChain);
      }
      verify(filterChain, times(3)).doFilter(any(), any());

      HttpServletRequest fourth = requestFor("POST", LOGIN_PATH, "203.0.113.9");
      filter.doFilter(fourth, response, filterChain);

      verify(filterChain, times(3)).doFilter(any(), any());
      verify(response).setStatus(429);
      verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Retry-After"), any());
    }

    @Test
    @DisplayName("tracks limits independently per client IP")
    void tracksLimitsIndependentlyPerIp() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 1, 100);
      HttpServletResponse response = mock(HttpServletResponse.class);
      capturingWriter(response);

      filter.doFilter(requestFor("POST", LOGIN_PATH, "1.1.1.1"), response, filterChain);
      filter.doFilter(requestFor("POST", LOGIN_PATH, "2.2.2.2"), response, filterChain);

      verify(filterChain, times(2)).doFilter(any(), any());
      verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("resolves the client IP via X-Forwarded-For's first hop before falling back")
    void usesForwardedForHeader() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 1, 100);
      HttpServletResponse response = mock(HttpServletResponse.class);
      capturingWriter(response);

      HttpServletRequest behindProxyA = requestFor("POST", LOGIN_PATH, "10.0.0.1");
      when(behindProxyA.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7");
      HttpServletRequest behindProxyB = requestFor("POST", LOGIN_PATH, "10.0.0.1");
      when(behindProxyB.getHeader("X-Forwarded-For")).thenReturn("198.51.100.7");

      filter.doFilter(behindProxyA, response, filterChain);
      // Same real client IP behind the shared proxy remoteAddr — should be rate limited on the
      // second call even though request.getRemoteAddr() is identical for every client.
      filter.doFilter(behindProxyB, response, filterChain);

      verify(filterChain, times(1)).doFilter(any(), any());
      verify(response).setStatus(429);
    }
  }

  @Nested
  @DisplayName("register path (app.rate-limit.register-per-hour)")
  class RegisterPath {

    @Test
    @DisplayName("applies its own separate limit from login")
    void hasIndependentLimitFromLogin() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 1, 1);
      HttpServletResponse response = mock(HttpServletResponse.class);
      capturingWriter(response);

      filter.doFilter(requestFor("POST", LOGIN_PATH, "9.9.9.9"), response, filterChain);
      filter.doFilter(requestFor("POST", REGISTER_PATH, "9.9.9.9"), response, filterChain);

      // One login + one register from the same IP — both within their own limit of 1.
      verify(filterChain, times(2)).doFilter(any(), any());
      verify(response, never()).setStatus(429);
    }
  }

  @Nested
  @DisplayName("unrelated requests")
  class UnrelatedRequests {

    @Test
    @DisplayName("passes through untouched — GET on the login path is not rate limited")
    void ignoresNonPostMethod() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 0, 0);
      HttpServletResponse response = mock(HttpServletResponse.class);

      filter.doFilter(requestFor("GET", LOGIN_PATH, "1.2.3.4"), response, filterChain);

      verify(filterChain).doFilter(any(), any());
      verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("passes through untouched — an unrelated path is never rate limited")
    void ignoresOtherPaths() throws Exception {
      AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 0, 0);
      HttpServletResponse response = mock(HttpServletResponse.class);

      filter.doFilter(requestFor("POST", OTHER_PATH, "1.2.3.4"), response, filterChain);

      verify(filterChain).doFilter(any(), any());
      verify(response, never()).setStatus(429);
    }
  }

  @Test
  @DisplayName("429 response body follows the ApiResponse error shape")
  void writes429BodyInApiResponseShape() throws Exception {
    AuthRateLimitFilter filter = new AuthRateLimitFilter(testObjectMapper(), 1, 100);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter sw = capturingWriter(response);

    filter.doFilter(requestFor("POST", LOGIN_PATH, "5.5.5.5"), response, filterChain);
    filter.doFilter(requestFor("POST", LOGIN_PATH, "5.5.5.5"), response, filterChain);

    assertThat(sw.toString()).contains("\"success\":false").contains("\"errorCode\":429");
  }
}
