package com.jobsapply.platform.shared.storage;

import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Encrypted file storage for personal documents. File names are generated (never derived from user input), so paths
 * cannot be manipulated; each file is encrypted with associated data binding it to its key and owner. Files are
 * write-once: a stored file is never modified.
 */
@Component
public class DocumentStore {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path root;
    private final PersonalDataCipher cipher;

    public DocumentStore(StorageProperties properties, PersonalDataCipher cipher) {
        this.root = properties.directory().toAbsolutePath().normalize();
        this.cipher = cipher;
    }

    /** @return the storage key */
    public String put(UUID owner, byte[] plaintext) {
        String key = UUID.randomUUID().toString();
        Path target = path(owner, key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, cipher.encryptBytes(plaintext, aad(owner, key)));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store document", e);
        }
    }

    public byte[] get(UUID owner, String key) {
        try {
            return cipher.decryptBytes(Files.readAllBytes(path(owner, key)), aad(owner, key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read document " + key, e);
        }
    }

    /** Best-effort removal of files written by an operation that did not commit. */
    public void deleteQuietly(UUID owner, String key) {
        try {
            Files.deleteIfExists(path(owner, key));
        } catch (IOException | IllegalArgumentException ignored) {
            // orphaned encrypted file; harmless
        }
    }

    private Path path(UUID owner, String key) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(owner.toString()).resolve(key + ".bin").normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }

    private static String aad(UUID owner, String key) {
        return "document|" + key + "|" + owner;
    }
}
