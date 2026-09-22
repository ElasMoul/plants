package com.plantpal.shared.filter;

import com.plantpal.session.config.SessionProperties;
import com.plantpal.session.entity.SessionCheckResult;
import com.plantpal.session.service.SessionRegistryService;
import com.plantpal.session.web.NonRenewingRequestMatcher;
import com.plantpal.shared.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String MDC_USER_ID = "userId";

  // Read by SessionController so GET /auth/session and POST /auth/session/renew don't have to
  // re-parse the bearer token to learn which jti they're acting on.
  public static final String JTI_ATTRIBUTE = "com.plantpal.session.jti";
  public static final String SESSION_REVOKED_REASON_HEADER = "X-Session-Revoked-Reason";

  private final JwtUtil jwtUtil;
  private final UserDetailsService userDetailsService;
  private final SessionRegistryService sessionRegistryService;
  private final NonRenewingRequestMatcher nonRenewingRequestMatcher;
  private final SessionProperties sessionProperties;

  public JwtAuthFilter(
      JwtUtil jwtUtil,
      UserDetailsService userDetailsService,
      SessionRegistryService sessionRegistryService,
      NonRenewingRequestMatcher nonRenewingRequestMatcher,
      SessionProperties sessionProperties) {
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
    this.sessionRegistryService = sessionRegistryService;
    this.nonRenewingRequestMatcher = nonRenewingRequestMatcher;
    this.sessionProperties = sessionProperties;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    // Deferred chat responses re-enter the security chain on another thread.
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader(AUTH_HEADER);

    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      String token = authHeader.substring(BEARER_PREFIX.length());
      try {
        String email = jwtUtil.extractEmail(token);
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
          UserDetails userDetails = userDetailsService.loadUserByUsername(email);
          if (userDetails.isEnabled()
              && userDetails.isAccountNonLocked()
              && jwtUtil.validateToken(token, userDetails)) {
            SessionCheckResult sessionCheck = null;
            if (jwtUtil.hasSessionRegistryClaims(token)) {
              String jti = jwtUtil.extractJti(token);
              request.setAttribute(JTI_ATTRIBUTE, jti);
              sessionCheck = checkSessionSafely(request, jti, token);
            }

            if (sessionCheck == null
                || sessionCheck.isValid()
                || !sessionProperties.isEnforcementEnabled()) {
              UsernamePasswordAuthenticationToken authToken =
                  new UsernamePasswordAuthenticationToken(
                      userDetails, null, userDetails.getAuthorities());
              authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
              SecurityContextHolder.getContext().setAuthentication(authToken);
              MDC.put(MDC_USER_ID, String.valueOf(jwtUtil.extractUserId(token)));
            } else {
              response.setHeader(
                  SESSION_REVOKED_REASON_HEADER, sessionCheck.getRevokedReason().name());
              log.info("Session enforcement rejected reason={}", sessionCheck.getRevokedReason());
            }
          }
        }
      } catch (Exception e) {
        log.warn("JWT authentication failed: {}", e.getMessage());
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_USER_ID);
    }
  }

  private SessionCheckResult checkSessionSafely(
      HttpServletRequest request, String jti, String token) {
    var absoluteExp = jwtUtil.extractSessionAbsoluteExp(token);
    try {
      return nonRenewingRequestMatcher.isNonRenewing(request)
          ? sessionRegistryService.status(jti, absoluteExp)
          : sessionRegistryService.validateAndSlide(jti, absoluteExp);
    } catch (RuntimeException e) {
      if (sessionProperties.isEnforcementEnabled()) {
        throw e;
      }
      log.warn("Session registry unavailable while enforcement is inert", e);
      return null;
    }
  }
}
