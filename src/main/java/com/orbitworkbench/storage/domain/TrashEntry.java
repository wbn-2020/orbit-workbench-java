package com.orbitworkbench.storage.domain;

public record TrashEntry(
        String originalStorageRef,
        String trashStorageRef
) {
}
