package com.plantpal.shared.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
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

  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.expiration-ms}")
  private long expirationMs;

  public String generateToken(UserDetails userDetails, Long userId) {
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim(CLAIM_USER_ID, userId)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expirationMs))
        .signWith(getSigningKey())
        .compact();
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
