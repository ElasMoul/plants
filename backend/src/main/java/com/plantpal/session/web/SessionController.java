package com.plantpal.session.web;

import com.plantpal.session.config.SessionProperties;
import com.plantpal.session.dto.SessionStatusResponse;
import com.plantpal.session.entity.RevokedReason;
import com.plantpal.session.entity.SessionCheckResult;
import com.plantpal.session.service.SessionRegistryService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.shared.filter.JwtAuthFilter;
import com.plantpal.shared.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-registry endpoints (apiSketch, wave 3). All three require an already-authenticated
 * request — JwtAuthFilter has already validated the bearer token's signature and stashed its jti as
 * a request attribute before any of these run.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Session", description = "Server-authoritative session status, renewal and logout")
public class SessionController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final SessionRegistryService sessionRegistryService;
  private final JwtUtil jwtUtil;
  private final SessionProperties sessionProperties;

  public SessionController(
      SessionRegistryService sessionRegistryService,
      JwtUtil jwtUtil,
      SessionProperties sessionProperties) {
    this.sessionRegistryService = sessionRegistryService;
    this.jwtUtil = jwtUtil;
    this.sessionProperties = sessionProperties;
  }

  @Operation(
      summary = "Am I still signed in, and for how long",
      description =
          "The only legitimate source of expiry truth for the client. Explicitly non-renewing —"
              + " polling it does not slide the idle window (D3).")
  @GetMapping("/session")
  public ResponseEntity<ApiResponse<SessionStatusResponse>> getSession(
      HttpServletRequest request, @RequestHeader("Authorization") String authHeader) {
    String jti = jti(request, authHeader);
    var absoluteExp = jwtUtil.extractSessionAbsoluteExp(bearerToken(authHeader));
    SessionCheckResult result = sessionRegistryService.status(jti, absoluteExp);
    return ResponseEntity.ok(ApiResponse.success(toResponse(result)));
  }

  @Operation(
      summary = "Keep me signed in",
      description = "Explicit user-intent renewal behind the pre-expiry warning's action (D2).")
  @PostMapping("/session/renew")
  public ResponseEntity<ApiResponse<SessionStatusResponse>> renewSession(
      HttpServletRequest request, @RequestHeader("Authorization") String authHeader) {
    String jti = jti(request, authHeader);
    var absoluteExp = jwtUtil.extractSessionAbsoluteExp(bearerToken(authHeader));
    SessionCheckResult result = sessionRegistryService.renew(jti, absoluteExp);
    return ResponseEntity.ok(ApiResponse.success(toResponse(result)));
  }

  @Operation(
      summary = "Sign out",
      description = "Revokes this session's registry record. Idempotent — always 204.")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest request, @RequestHeader("Authorization") String authHeader) {
    String jti = jti(request, authHeader);
    sessionRegistryService.revoke(jti, RevokedReason.LOGOUT);
    return ResponseEntity.noContent().build();
  }

  private String jti(HttpServletRequest request, String authHeader) {
    Object attribute = request.getAttribute(JwtAuthFilter.JTI_ATTRIBUTE);
    if (attribute != null) {
      return attribute.toString();
    }
    // Defensive fallback — JwtAuthFilter always sets this for a validly-signed bearer token,
    // which SecurityConfig guarantees for any request that reaches an @authenticated endpoint.
    return jwtUtil.extractJti(bearerToken(authHeader));
  }

  private String bearerToken(String authHeader) {
    return authHeader.startsWith(BEARER_PREFIX)
        ? authHeader.substring(BEARER_PREFIX.length())
        : authHeader;
  }

  private SessionStatusResponse toResponse(SessionCheckResult result) {
    return SessionStatusResponse.builder()
        .active(result.isValid())
        .secondsRemaining(result.getSecondsRemaining())
        .absoluteSecondsRemaining(result.getAbsoluteSecondsRemaining())
        .revokedReason(result.getRevokedReason() != null ? result.getRevokedReason().name() : null)
        .enforcementActive(sessionProperties.isEnforcementEnabled())
        .build();
  }
}
