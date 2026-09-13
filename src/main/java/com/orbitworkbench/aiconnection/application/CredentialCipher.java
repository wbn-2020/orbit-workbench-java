package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.CryptoProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CredentialCipher {
    private static final int IV_SIZE = 12;
    private static final int TAG_BITS = 128;
    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(CryptoProperties properties) {
        this.properties = properties;
    }

    public EncryptedCredential encrypt(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "API Key 不能为空");
        }
        byte[] key = decodeKey();
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedCredential(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),
                    iv, properties.keyVersion(), mask(value));
        } catch (Exception exception) {
            throw new IllegalStateException("凭据加密失败", exception);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv, Integer keyVersion) {
        if (ciphertext == null || iv == null || keyVersion == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "连接没有可用凭据");
        }
        if (iv.length != IV_SIZE || keyVersion != properties.keyVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "连接凭据版本不可用");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            // GCM 校验失败最常见的现实原因是主密钥换了（如密钥文件丢失后重建）：
            // 这是可恢复的用户状态而非服务器故障，按 409 引导重录 Key，不伪装 500。
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "凭据无法解密（应用主密钥可能已更换），请到「AI 连接」重新保存该账户的 API Key",
                    exception);
        }
    }

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "****";
    }

    private byte[] decodeKey() {
        try {
            byte[] key = Base64.getDecoder().decode(properties.masterKey());
            if (key.length != 32) {
                throw new IllegalArgumentException("master key length must be 32 bytes");
            }
            return key;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_PROVIDER_ERROR,
                    "应用主密钥配置无效");
        }
    }

    public record EncryptedCredential(byte[] ciphertext, byte[] iv, int keyVersion, String masked) {
    }
}
