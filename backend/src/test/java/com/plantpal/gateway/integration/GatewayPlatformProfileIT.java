package com.plantpal.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code platform} profile active on top of the base {@code test} profile ({@code @ActiveProfiles}
 * inherits and merges with {@link AbstractIntegrationTest}'s {@code "test"}). Proves
 * application-platform.yml wires the gateway on with no extra env vars, matching {@code
 * SPRING_PROFILES_ACTIVE=dev,platform} in a real deployment.
 */
@DisplayName("Gateway — platform profile")
@ActiveProfiles("platform")
class GatewayPlatformProfileIT extends AbstractIntegrationTest {

  @Autowired private GatewayProperties gatewayProperties;
  @Autowired private GatewayClient gatewayClient;

  @Test
  @DisplayName("context loads with the gateway bean enabled")
  void contextLoadsWithGatewayEnabled() {
    assertThat(gatewayClient).isNotNull();
    assertThat(gatewayProperties.enabled()).isTrue();
    // D045: host.docker.internal is the sanctioned tenant-app -> platform-service default
    assertThat(gatewayProperties.url()).isEqualTo("http://host.docker.internal:8085");
  }
}
