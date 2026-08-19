package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.shared.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CredentialCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MASTER_KEY_LENGTH = 32;
    private static final String MASK = "****";

    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCryptoService(CryptoProperties properties) {
        this.properties = properties;
    }

    public EncryptedCredential encrypt(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            byte[] ciphertext = cipher.doFinal(credential.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCredential(ciphertext, iv, keyVersion(), MASK);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据加密失败", exception);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv, Integer storedKeyVersion) {
        if (ciphertext == null || iv == null || storedKeyVersion == null) {
            throw new IllegalStateException("连接凭据不存在");
        }
        if (iv.length != IV_LENGTH || storedKeyVersion != keyVersion()) {
            throw new IllegalStateException("连接凭据密钥版本不可用");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv);
            byte[] plaintext = cipher.doFinal(ciphertext);
            try {
                return new String(plaintext, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("连接凭据解密失败", exception);
        }
    }

    public String mask() {
        return MASK;
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(masterKey(), "AES"),
                new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher;
    }

    private byte[] masterKey() {
        if (properties.masterKey() == null || properties.masterKey().isBlank()) {
            throw new IllegalStateException("ORBIT_MASTER_KEY 未配置");
        }
        try {
            byte[] key = Base64.getDecoder().decode(properties.masterKey());
            if (key.length != MASTER_KEY_LENGTH) {
                throw new IllegalStateException("ORBIT_MASTER_KEY 必须是 32 字节的 Base64 密钥");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ORBIT_MASTER_KEY 不是有效的 Base64 密钥", exception);
        }
    }

    private int keyVersion() {
        if (properties.keyVersion() <= 0) {
            throw new IllegalStateException("ORBIT_MASTER_KEY_VERSION 必须为正数");
        }
        return properties.keyVersion();
    }

    public record EncryptedCredential(byte[] ciphertext, byte[] iv, int keyVersion, String masked) {
    }
}
