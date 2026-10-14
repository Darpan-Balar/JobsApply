package com.jobsapply.platform.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesRfc9562Version7Uuids() {
        UUID id = Ids.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void encodesTimestampAndOrdersByTime() {
        UUID earlier = Ids.uuidV7(1_700_000_000_000L);
        UUID later = Ids.uuidV7(1_700_000_000_001L);
        assertThat(Ids.timestampMillis(earlier)).isEqualTo(1_700_000_000_000L);
        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void doesNotCollideInPractice() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(Ids.newId());
        }
        assertThat(ids).hasSize(100_000);
    }
}
