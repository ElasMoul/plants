package com.plantpal.shared.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup validation for secret env vars that otherwise fail late and cryptically.
 *
 * <p>Two live incidents motivated this (2026-07-08):
 *
 * <ul>
 *   <li>Placeholder VAPID keys crash-looped the whole app from {@code WebPushServiceImpl}'s
 *       constructor with {@code IllegalArgumentException: Invalid point encoding 0x-36} — no hint
 *       which env var was wrong.
 *   <li>A placeholder {@code JWT_SECRET} booted <em>fine</em>, then every login/registration 500'd
 *       with {@code DecodingException: Illegal base64 character: ':'} — zero indication it was
 *       config ({@code JwtUtil} only decodes the secret at signing time).
 * </ul>
 *
 * <p>Implemented as a {@link BeanFactoryPostProcessor} because BFPPs run before ANY regular bean is
 * instantiated — earlier than {@code WebPushServiceImpl}'s constructor (so the aggregated,
 * named-variable error wins over the cryptic BouncyCastle one) and earlier than an {@code
 * ApplicationRunner} or {@code InitializingBean} could guarantee. All failures are collected and
 * reported in ONE exception, not just the first.
 *
 * <p>The {@link Environment} is injected via {@link EnvironmentAware}, NOT the constructor: a BFPP
 * is instantiated during {@code invokeBeanFactoryPostProcessors}, which runs before {@code
 * AutowiredAnnotationBeanPostProcessor} is active, so constructor autowiring is not yet available
 * and Spring falls back to a (missing) no-arg constructor — {@code "No default constructor found"}.
 * {@code ApplicationContextAwareProcessor} (which services {@code EnvironmentAware}) is registered
 * in {@code prepareBeanFactory}, earlier still, so the setter path works at BFPP-instantiation time.
 */
@Component
public class SecretConfigValidator implements BeanFactoryPostProcessor, EnvironmentAware {

  private static final Logger log = LoggerFactory.getLogger(SecretConfigValidator.class);

  static final String JWT_FIX = "generate one: openssl rand -base64 64";
  static final String VAPID_FIX = "generate a pair: npx web-push generate-vapid-keys";
  private static final int JWT_MIN_BYTES = 32;
  private static final int VAPID_PUBLIC_KEY_BYTES = 65;
  private static final int VAPID_PRIVATE_KEY_BYTES = 32;
  private static final byte UNCOMPRESSED_POINT_PREFIX = 0x04;

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    List<String> failures = new ArrayList<>();

    String jwtSecret = readProperty("app.jwt.secret", "JWT_SECRET", JWT_FIX, failures);
    String vapidPublic =
        readProperty("app.web-push.public-key", "VAPID_PUBLIC_KEY", VAPID_FIX, failures);
    String vapidPrivate =
        readProperty("app.web-push.private-key", "VAPID_PRIVATE_KEY", VAPID_FIX, failures);

    if (jwtSecret != null) {
      failures.addAll(validateJwtSecret(jwtSecret));
    }
    if (vapidPublic != null) {
      failures.addAll(validateVapidPublicKey(vapidPublic));
    }
    if (vapidPrivate != null) {
      failures.addAll(validateVapidPrivateKey(vapidPrivate));
    }

    if (!failures.isEmpty()) {
      throw new ApplicationContextException(buildErrorMessage(failures));
    }
    log.debug("Secret configuration validated: JWT_SECRET, VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY");
  }

  /** JWT_SECRET: no placeholder markers, standard Base64, decodes to >= 32 bytes (HS256). */
  static List<String> validateJwtSecret(String value) {
    List<String> failures = new ArrayList<>();
    if (looksLikePlaceholder(value)) {
      failures.add("JWT_SECRET is not set to a real value — " + JWT_FIX);
      return failures;
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(value.trim());
    } catch (IllegalArgumentException e) {
      failures.add("JWT_SECRET is not valid Base64 (" + e.getMessage() + ") — " + JWT_FIX);
      return failures;
    }
    if (decoded.length < JWT_MIN_BYTES) {
      failures.add(
          "JWT_SECRET decodes to "
              + decoded.length
              + " bytes; HS256 needs at least "
              + JWT_MIN_BYTES
              + " — "
              + JWT_FIX);
    }
    return failures;
  }

  /** VAPID_PUBLIC_KEY: base64url, exactly 65 bytes, starting 0x04 (uncompressed P-256 point). */
  static List<String> validateVapidPublicKey(String value) {
    List<String> failures = new ArrayList<>();
    if (looksLikePlaceholder(value)) {
      failures.add("VAPID_PUBLIC_KEY is not set to a real value — " + VAPID_FIX);
      return failures;
    }
    byte[] decoded;
    try {
      decoded = Base64.getUrlDecoder().decode(value.trim());
    } catch (IllegalArgumentException e) {
      failures.add(
          "VAPID_PUBLIC_KEY is not valid base64url (" + e.getMessage() + ") — " + VAPID_FIX);
      return failures;
    }
    if (decoded.length != VAPID_PUBLIC_KEY_BYTES || decoded[0] != UNCOMPRESSED_POINT_PREFIX) {
      String firstByte = decoded.length == 0 ? "<empty>" : String.format("0x%02x", decoded[0]);
      failures.add(
          "VAPID_PUBLIC_KEY decodes to "
              + decoded.length
              + " bytes (first byte "
              + firstByte
              + "); an uncompressed P-256 public point must be exactly "
              + VAPID_PUBLIC_KEY_BYTES
              + " bytes starting 0x04 — "
              + VAPID_FIX);
    }
    return failures;
  }

  /**
   * VAPID_PRIVATE_KEY: base64url, exactly 32 bytes (P-256 scalar). The length check matters: the
   * .env.example placeholder ("your_vapid_private_key_here") is accidentally valid base64url, so
   * decode-alone would let it through to fail cryptically later.
   */
  static List<String> validateVapidPrivateKey(String value) {
    List<String> failures = new ArrayList<>();
    if (looksLikePlaceholder(value)) {
      failures.add("VAPID_PRIVATE_KEY is not set to a real value — " + VAPID_FIX);
      return failures;
    }
    byte[] decoded;
    try {
      decoded = Base64.getUrlDecoder().decode(value.trim());
    } catch (IllegalArgumentException e) {
      failures.add(
          "VAPID_PRIVATE_KEY is not valid base64url (" + e.getMessage() + ") — " + VAPID_FIX);
      return failures;
    }
    if (decoded.length != VAPID_PRIVATE_KEY_BYTES) {
      failures.add(
          "VAPID_PRIVATE_KEY decodes to "
              + decoded.length
              + " bytes; a P-256 private key must be exactly "
              + VAPID_PRIVATE_KEY_BYTES
              + " bytes — "
              + VAPID_FIX);
    }
    return failures;
  }

  /**
   * Placeholder markers: '&lt;', '&gt;' and ':' cannot appear in Base64/base64url and are exactly
   * what .env.example templates use (e.g. "&lt;generate: openssl rand -base64 64&gt;").
   */
  static boolean looksLikePlaceholder(String value) {
    return value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || value.indexOf(':') >= 0;
  }

  private String readProperty(String property, String envVar, String fix, List<String> failures) {
    String value;
    try {
      value = environment.getProperty(property);
    } catch (IllegalArgumentException e) {
      // ${ENV_VAR} placeholder in yml could not be resolved — the env var is not set at all
      value = null;
    }
    if (value == null || value.isBlank()) {
      failures.add(envVar + " is not set — " + fix);
      return null;
    }
    return value;
  }

  private String buildErrorMessage(List<String> failures) {
    StringBuilder sb =
        new StringBuilder("Invalid secret configuration — refusing to start (")
            .append(failures.size())
            .append(" problem")
            .append(failures.size() == 1 ? "" : "s")
            .append("):");
    for (String failure : failures) {
      sb.append(System.lineSeparator()).append("  - ").append(failure);
    }
    sb.append(System.lineSeparator())
        .append("Set real values in backend/.env (template: backend/.env.example).");
    return sb.toString();
  }
}
