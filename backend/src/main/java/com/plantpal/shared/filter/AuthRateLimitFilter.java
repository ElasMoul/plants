package com.plantpal.shared.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.shared.ratelimit.BoundedBucketStore;
import com.plantpal.shared.ratelimit.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limiting on {@code /api/v1/auth/login} and {@code /api/v1/auth/register} —
 * T-DEPLOY.3. Runs ahead of {@link JwtAuthFilter} / Spring Security's auth machinery since these
 * two endpoints are unauthenticated ({@code permitAll()} in SecurityConfig), so the only usable
 * rate-limit key here is the client IP, not a userId (unlike every other Bucket4j limiter in this
 * codebase — see ChatServiceImpl, TreatmentServiceImpl, IdentificationServiceImpl).
 *
 * <p>Wires the two previously-dead config keys: {@code app.rate-limit.auth-attempts-per-minute}
 * (login) and {@code app.rate-limit.register-per-hour} (register).
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final String REGISTER_PATH = "/api/v1/auth/register";
  // Bounds how many distinct client IPs each bucket store tracks at once (see
  // BoundedBucketStore's javadoc) — generous enough for real traffic, small enough to cap memory
  // under an IP-cycling attacker.
  private static final int MAX_TRACKED_IPS = 10_000;

  private final ObjectMapper objectMapper;
  private final int loginAttemptsPerMinute;
  private final int registerAttemptsPerHour;

  private final BoundedBucketStore loginBuckets = new BoundedBucketStore(MAX_TRACKED_IPS);
  private final BoundedBucketStore registerBuckets = new BoundedBucketStore(MAX_TRACKED_IPS);

  public AuthRateLimitFilter(
      ObjectMapper objectMapper,
      @Value("${app.rate-limit.auth-attempts-per-minute:5}") int loginAttemptsPerMinute,
      @Value("${app.rate-limit.register-per-hour:3}") int registerAttemptsPerHour) {
    this.objectMapper = objectMapper;
    this.loginAttemptsPerMinute = loginAttemptsPerMinute;
    this.registerAttemptsPerHour = registerAttemptsPerHour;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    RateLimitTarget target = resolveTarget(request);
    if (target == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientIp = ClientIpResolver.resolve(request);
    ConsumptionProbe probe = consume(target, clientIp);
    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
    log.warn(
        "Auth rate limit exceeded: path={}, clientIp={}, retryAfterSeconds={}",
        request.getRequestURI(),
        clientIp,
        retryAfterSeconds);
    writeTooManyRequests(response, retryAfterSeconds);
  }

  private RateLimitTarget resolveTarget(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return null;
    }
    if (LOGIN_PATH.equals(request.getRequestURI())) {
      return RateLimitTarget.LOGIN;
    }
    if (REGISTER_PATH.equals(request.getRequestURI())) {
      return RateLimitTarget.REGISTER;
    }
    return null;
  }

  private ConsumptionProbe consume(RateLimitTarget target, String clientIp) {
    return switch (target) {
      case LOGIN ->
          loginBuckets
              .resolveBucket(
                  clientIp, () -> newBucket(loginAttemptsPerMinute, Duration.ofMinutes(1)))
              .tryConsumeAndReturnRemaining(1);
      case REGISTER ->
          registerBuckets
              .resolveBucket(
                  clientIp, () -> newBucket(registerAttemptsPerHour, Duration.ofHours(1)))
              .tryConsumeAndReturnRemaining(1);
    };
  }

  private Bucket newBucket(int capacity, Duration period) {
    return Bucket.builder()
        .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, period).build())
        .build();
  }

  private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
      throws IOException {
    // The Servlet API's HttpServletResponse has no SC_TOO_MANY_REQUESTS constant (only defines
    // codes through the older RFC set) — use Spring's HttpStatus enum instead.
    int tooManyRequests = org.springframework.http.HttpStatus.TOO_MANY_REQUESTS.value();
    response.setStatus(tooManyRequests);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    ApiResponse<Void> body =
        ApiResponse.error(
            "Too many attempts — try again later", tooManyRequests, retryAfterSeconds);
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  private enum RateLimitTarget {
    LOGIN,
    REGISTER
  }
}
