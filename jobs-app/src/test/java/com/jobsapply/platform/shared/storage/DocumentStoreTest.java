package com.jobsapply.platform.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.shared.crypto.CryptoProperties;
import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStoreTest {

    @TempDir Path dir;

    private DocumentStore store() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        return new DocumentStore(new StorageProperties(dir), new PersonalDataCipher(new CryptoProperties(key, "k1", Map.of())));
    }

    @Test
    void storesEncryptedAndReadsBackExactBytes() throws Exception {
        DocumentStore store = store();
        UUID owner = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7 Jane Example resume \u0000\u00ff binary".getBytes(StandardCharsets.ISO_8859_1);

        String key = store.put(owner, pdf);

        assertThat(store.get(owner, key)).isEqualTo(pdf);
        try (Stream<Path> files = Files.walk(dir)) {
            Path stored = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            assertThat(stored.getFileName().toString()).isEqualTo(key + ".bin");
            assertThat(new String(Files.readAllBytes(stored), StandardCharsets.ISO_8859_1)).doesNotContain("Jane", "%PDF");
        }
    }

    @Test
    void filesAreBoundToTheirOwnerAndKeysCannotEscapeTheDirectory() {
        DocumentStore store = store();
        UUID owner = UUID.randomUUID();
        String key = store.put(owner, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> store.get(UUID.randomUUID(), key)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.get(owner, "../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
