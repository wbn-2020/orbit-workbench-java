package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.CryptoProperties;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class CredentialCipherTest {

    @Test
    void encryptsAndDecryptsCredential() {
        CredentialCipher cipher = cipherWithKey(11, 5);

        CredentialCipher.EncryptedCredential encrypted = cipher.encrypt("api-key-密钥");

        assertAll(
                () -> assertEquals("api-key-密钥",
                        cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion())),
                () -> assertEquals(12, encrypted.iv().length),
                () -> assertEquals(5, encrypted.keyVersion()),
                () -> assertEquals("****", encrypted.masked()));
    }

    @Test
    void usesDifferentIvForEachEncryption() {
        CredentialCipher cipher = cipherWithKey(12, 1);

        CredentialCipher.EncryptedCredential first = cipher.encrypt("same-value");
        CredentialCipher.EncryptedCredential second = cipher.encrypt("same-value");

        assertAll(
                () -> assertFalse(Arrays.equals(first.iv(), second.iv())),
                () -> assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext())));
    }

    @Test
    void rejectsDecryptionWithDifferentMasterKey() {
        CredentialCipher.EncryptedCredential encrypted =
                cipherWithKey(13, 1).encrypt("credential-value");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cipherWithKey(14, 1).decrypt(
                        encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion()));

        assertAll(
                () -> assertEquals("凭据解密失败", exception.getMessage()),
                () -> assertNotNull(exception.getCause()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankCredential(String credential) {
        ApiException exception = assertThrows(ApiException.class,
                () -> cipherWithKey(15, 1).encrypt(credential));

        assertApiException(exception, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void reportsInvalidMasterKeyAsConfigurationFailure() {
        assertInvalidMasterKey(null);
        assertInvalidMasterKey("not-base64!");
        assertInvalidMasterKey(base64Key(16, 31));
    }

    @Test
    void rejectsMissingOrMismatchedEncryptedMetadata() {
        CredentialCipher cipher = cipherWithKey(17, 3);
        CredentialCipher.EncryptedCredential encrypted = cipher.encrypt("credential");

        ApiException missing = assertThrows(ApiException.class,
                () -> cipher.decrypt(null, encrypted.iv(), 3));
        ApiException wrongIv = assertThrows(ApiException.class,
                () -> cipher.decrypt(encrypted.ciphertext(), new byte[11], 3));
        ApiException wrongVersion = assertThrows(ApiException.class,
                () -> cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), 2));

        assertAll(
                () -> assertApiException(missing, HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT),
                () -> assertApiException(wrongIv, HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT),
                () -> assertApiException(wrongVersion, HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT));
    }

    @Test
    void masksOnlyPresentValues() {
        CredentialCipher cipher = cipherWithKey(18, 1);

        assertAll(
                () -> assertEquals("****", cipher.mask("credential")),
                () -> assertNull(cipher.mask(null)),
                () -> assertNull(cipher.mask(" ")));
    }

    private CredentialCipher cipherWithKey(int fill, int keyVersion) {
        return new CredentialCipher(new CryptoProperties(base64Key(fill, 32), keyVersion));
    }

    private void assertInvalidMasterKey(String masterKey) {
        ApiException exception = assertThrows(ApiException.class,
                () -> new CredentialCipher(new CryptoProperties(masterKey, 1)).encrypt("credential"));
        assertApiException(exception, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_PROVIDER_ERROR);
    }

    private void assertApiException(ApiException exception,
                                    HttpStatus expectedStatus,
                                    ErrorCode expectedErrorCode) {
        assertAll(
                () -> assertEquals(expectedStatus, exception.getStatus()),
                () -> assertEquals(expectedErrorCode, exception.getErrorCode()));
    }

    private String base64Key(int fill, int length) {
        byte[] key = new byte[length];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
