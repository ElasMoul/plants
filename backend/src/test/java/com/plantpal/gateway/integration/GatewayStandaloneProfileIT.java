package com.plantpal.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * No {@code platform} profile active (just the base {@code test} profile, standing in for the
 * default/standalone boot — neither carries {@code platform.*} config). Proves the full context
 * still loads and the gateway is off, per D009: PlantPal runs with the platform entirely absent.
 */
@DisplayName("Gateway — standalone profile (no platform config)")
class GatewayStandaloneProfileIT extends AbstractIntegrationTest {

  @Autowired private GatewayProperties gatewayProperties;
  @Autowired private GatewayClient gatewayClient;

  @Test
  @DisplayName("context loads and gateway is disabled by default")
  void contextLoadsWithGatewayDisabled() {
    assertThat(gatewayClient).isNotNull();
    assertThat(gatewayProperties.enabled()).isFalse();
  }
}
