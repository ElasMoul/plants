package com.plantpal.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound from {@code platform.gateway.*}, which now lives only in {@code application-platform.yml}
 * (the {@code platform} profile). The {@code @DefaultValue}s let this bind cleanly when that
 * profile — and thus the whole {@code platform.gateway} prefix — is entirely absent, which is the
 * standalone/default-profile case (D009: PlantPal runs with the platform absent).
 */
@ConfigurationProperties(prefix = "platform.gateway")
public record GatewayProperties(
    @DefaultValue("false") boolean enabled, @DefaultValue("http://localhost:8085") String url) {}
