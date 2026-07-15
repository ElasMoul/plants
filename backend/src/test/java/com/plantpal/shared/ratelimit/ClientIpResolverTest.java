package com.plantpal.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClientIpResolver")
class ClientIpResolverTest {

  @Test
  @DisplayName("uses the first hop of X-Forwarded-For when present (Railway's reverse proxy)")
  void usesFirstHopOfForwardedFor() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1, 10.0.0.2");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.5");
  }

  @Test
  @DisplayName("trims whitespace around the first hop")
  void trimsWhitespace() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.5  , 10.0.0.1");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.5");
  }

  @Test
  @DisplayName("falls back to getRemoteAddr() when the header is absent")
  void fallsBackToRemoteAddrWhenHeaderAbsent() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.10");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.168.1.10");
  }

  @Test
  @DisplayName("falls back to getRemoteAddr() when the header is blank")
  void fallsBackToRemoteAddrWhenHeaderBlank() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
    when(request.getRemoteAddr()).thenReturn("192.168.1.10");

    assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.168.1.10");
  }
}
