package com.plantpal.shared.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_JTI = "jti";
  // Session-registry absolute cap (D4/ADR-3, wave 3) — deliberately a claim of its own rather
  // than the token's own `exp`. It travels signed and Redis-independent (a wiped-and-restored
  // registry can't extend a session past it) without changing today's `exp`-driven token
  // lifetime, which ADR-3's full convergence is left as an explicit wave-5 decision (see
  // SessionProperties' Javadoc).
  private static final String CLAIM_SESSION_ABSOLUTE_EXP = "sessionAbsoluteExp";

  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.expiration-ms}")
  private long expirationMs;

  @Value("${app.session.absolute-cap-hours:12}")
  private int sessionAbsoluteCapHours;

  public String generateToken(UserDetails userDetails, Long userId) {
    Date now = new Date();
    Date sessionAbsoluteExp = Date.from(now.toInstant().plus(Duration.ofHours(sessionAbsoluteCapHours)));
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim(CLAIM_USER_ID, userId)
        .claim(CLAIM_JTI, UUID.randomUUID().toString())
        .claim(CLAIM_SESSION_ABSOLUTE_EXP, sessionAbsoluteExp.getTime())
        .issuedAt(now)
        .expiration(new Date(System.currentTimeMillis() + expirationMs))
        .signWith(getSigningKey())
        .compact();
  }

  public String extractJti(String token) {
    return extractClaim(token, claims -> claims.get(CLAIM_JTI, String.class));
  }

  public Instant extractSessionAbsoluteExp(String token) {
    return extractClaim(
        token, claims -> Instant.ofEpochMilli(claims.get(CLAIM_SESSION_ABSOLUTE_EXP, Long.class)));
  }

  /**
   * Returns whether this token was issued after the session registry was introduced. Existing
   * unexpired tokens deliberately remain valid while the registry is rolling out inertly.
   */
  public boolean hasSessionRegistryClaims(String token) {
    return extractClaim(token, claims -> claims.get(CLAIM_JTI, String.class)) != null
        && extractClaim(token, claims -> claims.get(CLAIM_SESSION_ABSOLUTE_EXP, Long.class)) != null;
  }

  public boolean validateToken(String token, UserDetails userDetails) {
    try {
      String email = extractEmail(token);
      return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("JWT validation failed: {}", e.getMessage());
      return false;
    }
  }

  public String extractEmail(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public Long extractUserId(String token) {
    return extractClaim(token, claims -> claims.get(CLAIM_USER_ID, Long.class));
  }

  private <T> T extractClaim(String token, Function<Claims, T> resolver) {
    Claims claims =
        Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    return resolver.apply(claims);
  }

  private boolean isTokenExpired(String token) {
    return extractClaim(token, Claims::getExpiration).before(new Date());
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
  }
}
