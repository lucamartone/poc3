package com.poc.connectivity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectivity.socket")
public record SocketProperties(
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
