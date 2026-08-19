package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orbitworkbench.shared.config.CryptoProperties;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CredentialCryptoServiceTest {

    @Test
    void encryptsAndDecryptsCredential() {
        CredentialCryptoService service = serviceWithKey(1, 7);

        CredentialCryptoService.EncryptedCredential encrypted = service.encrypt("密钥-value-123");

        assertAll(
                () -> assertEquals("密钥-value-123",
                        service.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion())),
                () -> assertEquals(12, encrypted.iv().length),
                () -> assertEquals(7, encrypted.keyVersion()),
                () -> assertEquals("****", encrypted.masked()),
                () -> assertEquals("****", service.mask()));
    }

    @Test
    void usesDifferentIvForEachEncryption() {
        CredentialCryptoService service = serviceWithKey(2, 1);

        CredentialCryptoService.EncryptedCredential first = service.encrypt("same-value");
        CredentialCryptoService.EncryptedCredential second = service.encrypt("same-value");

        assertAll(
                () -> assertFalse(Arrays.equals(first.iv(), second.iv())),
                () -> assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext())));
    }

    @Test
    void rejectsDecryptionWithDifferentMasterKey() {
        CredentialCryptoService.EncryptedCredential encrypted =
                serviceWithKey(3, 1).encrypt("credential-value");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serviceWithKey(4, 1).decrypt(
                        encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion()));

        assertAll(
                () -> assertEquals("连接凭据解密失败", exception.getMessage()),
                () -> assertNotNull(exception.getCause()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankCredential(String credential) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> serviceWithKey(5, 1).encrypt(credential));

        assertEquals("凭据不能为空", exception.getMessage());
    }

    @Test
    void rejectsMissingInvalidAndWrongLengthMasterKeys() {
        assertEncryptionFailure(new CryptoProperties(null, 1), "ORBIT_MASTER_KEY 未配置");
        assertEncryptionFailure(new CryptoProperties("not-base64!", 1),
                "ORBIT_MASTER_KEY 不是有效的 Base64 密钥");
        assertEncryptionFailure(new CryptoProperties(base64Key(6, 31), 1),
                "ORBIT_MASTER_KEY 必须是 32 字节的 Base64 密钥");
    }

    @Test
    void rejectsNonPositiveKeyVersion() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new CredentialCryptoService(
                        new CryptoProperties(base64Key(7, 32), 0)).encrypt("credential"));

        assertEquals("ORBIT_MASTER_KEY_VERSION 必须为正数", exception.getMessage());
    }

    @Test
    void rejectsMissingOrMismatchedEncryptedMetadata() {
        CredentialCryptoService service = serviceWithKey(8, 3);
        CredentialCryptoService.EncryptedCredential encrypted = service.encrypt("credential");

        assertAll(
                () -> assertEquals("连接凭据不存在",
                        assertThrows(IllegalStateException.class,
                                () -> service.decrypt(null, encrypted.iv(), 3)).getMessage()),
                () -> assertEquals("连接凭据密钥版本不可用",
                        assertThrows(IllegalStateException.class,
                                () -> service.decrypt(encrypted.ciphertext(), new byte[11], 3)).getMessage()),
                () -> assertEquals("连接凭据密钥版本不可用",
                        assertThrows(IllegalStateException.class,
                                () -> service.decrypt(encrypted.ciphertext(), encrypted.iv(), 2)).getMessage()));
    }

    private CredentialCryptoService serviceWithKey(int fill, int keyVersion) {
        return new CredentialCryptoService(new CryptoProperties(base64Key(fill, 32), keyVersion));
    }

    private void assertEncryptionFailure(CryptoProperties properties, String expectedMessage) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new CredentialCryptoService(properties).encrypt("credential"));
        assertEquals(expectedMessage, exception.getMessage());
    }

    private String base64Key(int fill, int length) {
        byte[] key = new byte[length];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
