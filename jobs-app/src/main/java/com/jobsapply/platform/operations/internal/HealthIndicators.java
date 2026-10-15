package com.jobsapply.platform.operations.internal;

import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import com.jobsapply.platform.shared.storage.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Health of what the platform depends on beyond the database. Details are shown to administrators only. A backlog of
 * undelivered events is a WARNING (the audit log lags), not DOWN: the platform keeps working and delivery is retried.
 */
@Configuration(proxyBeanMethods = false)
class HealthIndicators {

    static final Status WARNING = new Status("WARNING", "Working, but something needs attention");
    static final Duration STALE_PUBLICATION = Duration.ofMinutes(10);

    /** The encryption key is present and works (a round trip with throwaway data). */
    @Bean
    HealthIndicator encryptionHealthIndicator(PersonalDataCipher cipher) {
        return () -> {
            try {
                String probe = "health-probe";
                boolean ok = probe.equals(cipher.decrypt(cipher.encrypt(probe, "health|probe"), "health|probe"));
                return ok ? Health.up().build() : Health.down().withDetail("reason", "round trip mismatch").build();
            } catch (RuntimeException e) {
                return Health.down().withDetail("reason", "the encryption key is missing or unusable").build();
            }
        };
    }

    /** Encrypted documents can be written. */
    @Bean
    HealthIndicator documentStoreHealthIndicator(StorageProperties storage) {
        return () -> {
            Path root = storage.directory().toAbsolutePath().normalize();
            try {
                Files.createDirectories(root);
                Path probe = Files.createTempFile(root, ".health-", ".tmp");
                Files.delete(probe);
                return Health.up().withDetail("directory", root.toString()).build();
            } catch (Exception e) {
                return Health.down().withDetail("directory", root.toString()).withDetail("reason", "not writable").build();
            }
        };
    }

    /** Events stored but not delivered yet (e.g. the audit log could not be written). */
    @Bean
    HealthIndicator eventPublicationsHealthIndicator(JdbcTemplate jdbc, Clock clock) {
        return () -> {
            Map<String, Object> row = jdbc.queryForMap(
                    "select count(*) as pending, min(publication_date) as oldest from event_publication where completion_date is null");
            long pending = ((Number) row.get("pending")).longValue();
            Instant oldest = row.get("oldest") instanceof Timestamp t ? t.toInstant() : null;
            boolean stale = oldest != null && oldest.isBefore(clock.instant().minus(STALE_PUBLICATION));
            Health.Builder health = stale ? Health.status(WARNING) : Health.up();
            health.withDetail("pending", pending);
            if (oldest != null) {
                health.withDetail("oldestPending", oldest.toString());
            }
            return health.build();
        };
    }
}
