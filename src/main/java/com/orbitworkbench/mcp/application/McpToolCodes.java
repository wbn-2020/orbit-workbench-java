package com.orbitworkbench.mcp.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class McpToolCodes {

    private McpToolCodes() {
    }

    public static String code(Long serverId, String toolName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((serverId + "|" + toolName).getBytes(StandardCharsets.UTF_8));
            return "mcp_" + serverId + "_"
                    + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
