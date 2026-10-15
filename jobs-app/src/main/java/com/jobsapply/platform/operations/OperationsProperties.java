package com.jobsapply.platform.operations;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jobs.operations.*}.
 *
 * @param sync scheduled synchronization of every active employer binding
 * @param retention scheduled deletion of operational data that is no longer useful (never personal data or audit)
 */
@Validated
@ConfigurationProperties("jobs.operations")
public record OperationsProperties(@Valid Sync sync, @Valid Retention retention) {

    public OperationsProperties {
        sync = Objects.requireNonNullElse(sync, new Sync(false, null));
        retention = Objects.requireNonNullElse(retention, new Retention(false, null, null, null));
    }

    /** @param cron Spring cron expression, in the server's time zone */
    public record Sync(boolean enabled, String cron) {
        public Sync {
            cron = cron == null || cron.isBlank() ? "0 0 6,12,18 * * *" : cron;
        }
    }

    /**
     * @param syncRuns how long finished sync runs (and their error details) are kept
     * @param eventPublications how long delivered event publications are kept
     */
    public record Retention(boolean enabled, String cron, Duration syncRuns, Duration eventPublications) {
        public Retention {
            cron = cron == null || cron.isBlank() ? "0 30 3 * * *" : cron;
            syncRuns = Objects.requireNonNullElse(syncRuns, Duration.ofDays(90));
            eventPublications = Objects.requireNonNullElse(eventPublications, Duration.ofDays(7));
        }
    }
}
