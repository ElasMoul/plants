package com.plantpal.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.gateway")
public record GatewayProperties(boolean enabled, String url) {}
