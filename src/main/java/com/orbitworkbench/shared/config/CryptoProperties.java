package com.orbitworkbench.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit.crypto")
public record CryptoProperties(String masterKey, int keyVersion) {
}

