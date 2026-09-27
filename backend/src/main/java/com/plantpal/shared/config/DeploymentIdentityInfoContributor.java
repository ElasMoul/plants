package com.plantpal.shared.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Reports the running app's own identity under {@code GET /actuator/info} → {@code deployment}
 * (D113 dev delivery). A deployment tool, Factory or runtime reads it to observe which revision a
 * URL actually serves, instead of trusting configuration.
 *
 * <p>{@code revision} comes from {@code APP_REVISION}, stamped into the image at build time by
 * {@code backend/Dockerfile}'s build arg from the exact tree that was compiled. {@code
 * deploymentId} and {@code environment} come from the container env set by the dev-delivery stack.
 * Anything absent or malformed is reported as {@code null} — "not reported", never a default. A
 * short or non-hex revision is {@code null}, not echoed, so a caller can never match on it.
 *
 * <p>This shape is plantpal-native until contracts publishes a tagged app-identity schema (demand
 * {@code plantpal-20260927-contracts-app-deploy-receipt-and-identity}).
 */
@Component
public class DeploymentIdentityInfoContributor implements InfoContributor {

  static final String APP_IDENTITY = "plantpal";
  private static final Pattern FULL_SHA = Pattern.compile("^[0-9a-f]{40}$");
  private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

  private final String revision;
  private final String deploymentId;
  private final String environment;

  public DeploymentIdentityInfoContributor(
      @Value("${APP_REVISION:}") String revision,
      @Value("${APP_DEPLOYMENT_ID:}") String deploymentId,
      @Value("${APP_ENVIRONMENT:}") String environment) {
    this.revision = matchOrNull(revision, FULL_SHA);
    this.deploymentId = matchOrNull(deploymentId, SAFE_TOKEN);
    this.environment = matchOrNull(environment, SAFE_TOKEN);
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail("deployment", identity());
  }

  Map<String, Object> identity() {
    // LinkedHashMap, not Map.of: null values are the point (unknown stays unknown).
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("appIdentity", APP_IDENTITY);
    identity.put("revision", revision);
    identity.put("deploymentId", deploymentId);
    identity.put("environment", environment);
    return identity;
  }

  private static String matchOrNull(String value, Pattern pattern) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return pattern.matcher(trimmed).matches() ? trimmed : null;
  }
}
