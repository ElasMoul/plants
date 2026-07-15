package com.plantpal.shared.config;

import com.plantpal.shared.filter.AuthRateLimitFilter;
import com.plantpal.shared.filter.JwtAuthFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // Swagger UI's bundled index.html boots itself via an inline <script> block and inline
  // <style> — springdoc serves it from the classpath (not a CDN), but the strict CSP below
  // still has to relax script-src/style-src for exactly these paths (T-DEPLOY.3).
  private static final RequestMatcher SWAGGER_MATCHER =
      new OrRequestMatcher(
          new AntPathRequestMatcher("/swagger-ui/**"),
          new AntPathRequestMatcher("/swagger-ui.html"),
          new AntPathRequestMatcher("/v3/api-docs/**"));

  // Comma-separated list — e.g. "http://localhost:4200,https://plantpal.app"
  @Value("${app.cors.allowed-origins:http://localhost:4200}")
  private String allowedOriginsRaw;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthFilter jwtAuthFilter, AuthRateLimitFilter authRateLimitFilter)
      throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(this::configureHeaders)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/photos/**",
                        "/api/v1/photos/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, e) ->
                        response.sendError(
                            jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)));

    return http.build();
  }

  /**
   * T-DEPLOY.3: nosniff + frame-deny + HSTS (Spring defaults — HSTS only writes on an
   * already-secure request, a no-op over plain HTTP) plus a same-origin CSP. The CSP is split in
   * two via {@link DelegatingRequestMatcherHeaderWriter} so Swagger UI's inline bootstrap
   * script/styles keep working without loosening the policy for the rest of the API.
   */
  private void configureHeaders(HeadersConfigurer<HttpSecurity> headers) {
    RequestMatcher nonSwaggerMatcher = new NegatedRequestMatcher(SWAGGER_MATCHER);
    ContentSecurityPolicyHeaderWriter strictCsp =
        new ContentSecurityPolicyHeaderWriter(
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
                + " connect-src 'self'; frame-ancestors 'none'");
    ContentSecurityPolicyHeaderWriter swaggerCsp =
        new ContentSecurityPolicyHeaderWriter(
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self'"
                + " 'unsafe-inline'; img-src 'self' data:; connect-src 'self'");

    headers
        .contentTypeOptions(Customizer.withDefaults())
        .frameOptions(frame -> frame.deny())
        .httpStrictTransportSecurity(Customizer.withDefaults())
        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(SWAGGER_MATCHER, swaggerCsp))
        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(nonSwaggerMatcher, strictCsp));
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("X-Correlation-ID"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
