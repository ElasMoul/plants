package com.plantpal.statefeed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound from {@code platform.statefeed.*}, which lives only in {@code application-platform.yml}
 * (the {@code platform} profile) — same pattern as {@code com.plantpal.gateway.GatewayProperties}.
 * The {@code @DefaultValue}s let this bind cleanly when that profile — and thus the whole {@code
 * platform.statefeed} prefix — is entirely absent, which is the standalone/default-profile case
 * (D009: PlantPal runs with the platform absent, and reads zero {@code platform.*} keys then).
 *
 * <p>{@code url} defaults to {@code http://localhost:8080} — the state-feed's dev host port per the
 * platform port-block ruling recorded in {@code PROGRESS.md} (2026-07-08 entry: PlantPal's own
 * backend was remapped 8080 -> 8180 specifically because 8080 collided with state-feed).
 */
@ConfigurationProperties(prefix = "platform.statefeed")
public record StateFeedProperties(
    @DefaultValue("false") boolean enabled, @DefaultValue("http://localhost:8080") String url) {}
