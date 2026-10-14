package com.jobsapply.platform.shared.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jobs.crypto.*}. Keys are base64-encoded 32-byte AES keys supplied via the environment (never in config files).
 *
 * @param key the active key (from {@code JOBS_ENCRYPTION_KEY})
 * @param keyId identifier written next to ciphertext, so the key can be rotated
 * @param previousKeys retired keys by id, still needed to read older data
 */
@ConfigurationProperties("jobs.crypto")
public record CryptoProperties(String key, String keyId, Map<String, String> previousKeys) {

    public CryptoProperties {
        keyId = keyId == null || keyId.isBlank() ? "k1" : keyId;
        previousKeys = previousKeys == null ? Map.of() : Map.copyOf(previousKeys);
    }

    @Override
    public String toString() {
        return "CryptoProperties[keyId=" + keyId + ", key=<redacted>]";
    }
}
