package com.jobsapply.platform.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encrypts personal data at rest with AES-256-GCM (JDK only). Ciphertext is stored as a small JSON envelope
 * ({@code {"v":1,"alg":"AES-256-GCM","kid":..,"iv":..,"ct":..}}) so it fits jsonb columns and supports key rotation.
 *
 * <p>The associated data binds each ciphertext to its row (e.g. version id + owner id): ciphertext copied into
 * another row — or another user's row — fails to decrypt. Content fingerprints use HMAC-SHA256 with a key derived from
 * the encryption key, so stored hashes cannot be used to guess plaintext.
 *
 * <p>Fails fast at startup without a valid key: silently generating one would make data unreadable after a restart.
 */
@Component
public class PersonalDataCipher {

    private static final String ALG = "AES-256-GCM";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String activeKeyId;
    private final Map<String, SecretKeySpec> keys = new HashMap<>();
    private final SecretKeySpec hmacKey;

    public PersonalDataCipher(CryptoProperties properties) {
        this.activeKeyId = properties.keyId();
        SecretKeySpec active = decodeKey("jobs.crypto.key (JOBS_ENCRYPTION_KEY)", properties.key());
        keys.put(activeKeyId, active);
        properties.previousKeys().forEach((id, value) -> keys.put(id, decodeKey("jobs.crypto.previous-keys." + id, value)));
        this.hmacKey = new SecretKeySpec(sha256(("hmac|" + activeKeyId + "|").getBytes(StandardCharsets.UTF_8), active.getEncoded()), "HmacSHA256");
    }

    /** @return the JSON envelope */
    public String encrypt(String plaintext, String associatedData) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("v", 1);
            envelope.put("alg", ALG);
            envelope.put("kid", activeKeyId);
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("ct", Base64.getEncoder().encodeToString(ciphertext));
            return json.writeValueAsString(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** @throws IllegalStateException if the envelope was tampered with, moved to another row, or its key is unknown */
    public String decrypt(String envelopeJson, String associatedData) {
        Map<?, ?> envelope = json.readValue(envelopeJson, Map.class);
        if (!ALG.equals(envelope.get("alg"))) {
            throw new IllegalStateException("Not an encrypted envelope");
        }
        SecretKeySpec key = keys.get(String.valueOf(envelope.get("kid")));
        if (key == null) {
            throw new IllegalStateException("Unknown encryption key id: " + envelope.get("kid"));
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(String.valueOf(envelope.get("iv")))));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(String.valueOf(envelope.get("ct")))), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed: data was altered, moved, or encrypted with another key");
        }
    }

    private static final byte[] FILE_MAGIC = {'J', 'A', 'E', '1'};

    /**
     * Encrypts binary content (files). Layout: {@code "JAE1" | kid length (1 byte) | kid | 12-byte IV | ciphertext+tag}.
     */
    public byte[] encryptBytes(byte[] plaintext, String associatedData) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] kid = activeKeyId.getBytes(StandardCharsets.UTF_8);
            ByteBuffer out = ByteBuffer.allocate(FILE_MAGIC.length + 1 + kid.length + IV_BYTES + ciphertext.length);
            out.put(FILE_MAGIC).put((byte) kid.length).put(kid).put(iv).put(ciphertext);
            return out.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] stored, String associatedData) {
        ByteBuffer in = ByteBuffer.wrap(stored);
        byte[] magic = new byte[FILE_MAGIC.length];
        if (stored.length < FILE_MAGIC.length + 1 + IV_BYTES) {
            throw new IllegalStateException("Not an encrypted file");
        }
        in.get(magic);
        if (!Arrays.equals(magic, FILE_MAGIC)) {
            throw new IllegalStateException("Not an encrypted file");
        }
        byte[] kid = new byte[in.get() & 0xFF];
        in.get(kid);
        SecretKeySpec key = keys.get(new String(kid, StandardCharsets.UTF_8));
        if (key == null) {
            throw new IllegalStateException("Unknown encryption key id: " + new String(kid, StandardCharsets.UTF_8));
        }
        byte[] iv = new byte[IV_BYTES];
        in.get(iv);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed: data was altered, moved, or encrypted with another key");
        }
    }

    /** Keyed fingerprint (hex HMAC-SHA256) for equality checks on personal data without exposing it. */
    public String fingerprint(String plaintext) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static SecretKeySpec decodeKey(String name, String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(name + " is not set. Generate one with `openssl rand -base64 32`, put it in .env "
                    + "(JOBS_ENCRYPTION_KEY=...) and back it up: without it, encrypted personal data cannot be read.");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " is not valid base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException(name + " must decode to 32 bytes (AES-256); got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] sha256(byte[] prefix, byte[] key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix);
            return digest.digest(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
