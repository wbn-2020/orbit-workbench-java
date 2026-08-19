package com.orbitworkbench.shared.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orbit.storage")
public record StorageProperties(Path root, long maxDocumentBytes, long maxArtifactBytes) {
}
