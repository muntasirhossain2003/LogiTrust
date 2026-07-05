package com.logitrust.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.logitrust.config.EncryptionProperties;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        encryptionService = new EncryptionService(new EncryptionProperties(key));
    }

    @Test
    void encryptThenDecrypt_roundTripsExactPlaintext() {
        String plaintext = "{\"temperatureC\":4.5,\"humidityPct\":60.0}";

        String ciphertext = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String plaintext = "same input";

        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        // Random IV per call means identical plaintext never produces identical
        // ciphertext - otherwise an observer could spot repeated readings.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void decrypt_rejectsTamperedCiphertext() {
        String ciphertext = encryptionService.encrypt("sensitive reading");
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[raw.length - 1] ^= 0x01; // flip a bit in the auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_rejectsKeyOfWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
