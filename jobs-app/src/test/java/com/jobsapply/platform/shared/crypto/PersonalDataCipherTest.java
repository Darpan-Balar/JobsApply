package com.jobsapply.platform.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalDataCipherTest {

    static final String KEY_1 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    static final String KEY_2 = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    final PersonalDataCipher cipher = new PersonalDataCipher(new CryptoProperties(KEY_1, "k1", Map.of()));

    @Test
    void roundTripsAndHidesThePlaintext() {
        String envelope = cipher.encrypt("{\"email\":\"jane@example.com\"}", "row-1");

        assertThat(envelope).contains("\"alg\":\"AES-256-GCM\"", "\"kid\":\"k1\"").doesNotContain("jane", "example.com");
        assertThat(cipher.decrypt(envelope, "row-1")).isEqualTo("{\"email\":\"jane@example.com\"}");
        assertThat(cipher.encrypt("same", "row-1")).as("random IV per encryption").isNotEqualTo(cipher.encrypt("same", "row-1"));
    }

    @Test
    void ciphertextIsBoundToItsRow() {
        String envelope = cipher.encrypt("secret", "row-1");
        assertThatThrownBy(() -> cipher.decrypt(envelope, "row-2")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("altered, moved");
    }

    @Test
    void tamperingIsDetected() {
        String envelope = cipher.encrypt("secret value", "row-1");
        String tampered = envelope.replaceFirst("\"ct\":\"(.)", "\"ct\":\"" + (envelope.contains("\"ct\":\"A") ? "B" : "A"));
        assertThatThrownBy(() -> cipher.decrypt(tampered, "row-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retiredKeysStillDecryptAfterRotation() {
        String old = cipher.encrypt("written with k1", "row");
        PersonalDataCipher rotated = new PersonalDataCipher(new CryptoProperties(KEY_2, "k2", Map.of("k1", KEY_1)));

        assertThat(rotated.decrypt(old, "row")).isEqualTo("written with k1");
        assertThat(rotated.encrypt("new", "row")).contains("\"kid\":\"k2\"");
        PersonalDataCipher withoutOldKey = new PersonalDataCipher(new CryptoProperties(KEY_2, "k2", Map.of()));
        assertThatThrownBy(() -> withoutOldKey.decrypt(old, "row")).hasMessageContaining("Unknown encryption key id: k1");
    }

    @Test
    void refusesToStartWithoutAValidKey() {
        assertThatThrownBy(() -> new PersonalDataCipher(new CryptoProperties("", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("openssl rand -base64 32");
        assertThatThrownBy(() -> new PersonalDataCipher(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[16]), null, null)))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new PersonalDataCipher(new CryptoProperties("not base64!", null, null)))
                .hasMessageContaining("not valid base64");
    }

    @Test
    void fingerprintsAreKeyedSoTheyCannotBeRecomputedWithoutTheKey() {
        PersonalDataCipher other = new PersonalDataCipher(new CryptoProperties(KEY_2, "k1", Map.of()));
        assertThat(cipher.fingerprint("x")).isEqualTo(cipher.fingerprint("x")).hasSize(64);
        assertThat(cipher.fingerprint("x")).isNotEqualTo(other.fingerprint("x"));
    }

    @Test
    void propertiesNeverPrintTheKey() {
        assertThat(new CryptoProperties(KEY_1, "k1", Map.of()).toString()).doesNotContain(KEY_1);
    }
}
