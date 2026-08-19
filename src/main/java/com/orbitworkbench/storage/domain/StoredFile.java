package com.orbitworkbench.storage.domain;

public record StoredFile(
        String storageRef,
        long sizeBytes,
        String contentHash
) {
}
