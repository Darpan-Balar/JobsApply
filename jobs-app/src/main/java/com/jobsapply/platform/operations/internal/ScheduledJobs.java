package com.jobsapply.platform.operations.internal;

import com.jobsapply.platform.discovery.SyncOutcome;
import com.jobsapply.platform.discovery.SyncReport;
import com.jobsapply.platform.discovery.SyncRunQueryService;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.operations.OperationsProperties;
import com.jobsapply.platform.shared.persistence.ClusterLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled jobs. Each runs under a cluster-wide lock, so overlapping ticks or a second instance skip instead of
 * running twice; synchronization additionally has per-binding single-flight in the database (ADR-0008). Disabled
 * jobs return immediately (their properties decide; the schedule itself always exists).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    static final String SYNC_LOCK = "jobs:scheduled-sync";
    static final String RETENTION_LOCK = "jobs:retention";

    private final OperationsProperties properties;
    private final ClusterLock lock;
    private final SyncService sync;
    private final SyncRunQueryService syncRuns;
    private final CompletedEventPublications completedPublications;
    private final MeterRegistry meters;
    private final Clock clock;

    public record SyncSummary(int bindings, long succeeded, long partial, long failed, long skippedRunning) {}

    public record RetentionSummary(int syncRunsDeleted) {}

    @Scheduled(cron = "${jobs.operations.sync.cron:0 0 6,12,18 * * *}")
    void scheduledSync() {
        if (properties.sync().enabled()) {
            runSync();
        }
    }

    @Scheduled(cron = "${jobs.operations.retention.cron:0 30 3 * * *}")
    void scheduledRetention() {
        if (properties.retention().enabled()) {
            runRetention();
        }
    }

    /** @return what happened, or {@code null} if another run held the lock */
    public SyncSummary runSync() {
        SyncSummary summary = lock.runExclusively(SYNC_LOCK, () -> {
            List<SyncReport> reports = sync.sync(null, null, SyncTrigger.SCHEDULED, false);
            return new SyncSummary(reports.size(),
                    reports.stream().filter(r -> r.outcome() == SyncOutcome.SUCCEEDED).count(),
                    reports.stream().filter(r -> r.outcome() == SyncOutcome.PARTIAL).count(),
                    reports.stream().filter(r -> r.outcome() == SyncOutcome.FAILED).count(),
                    reports.stream().filter(r -> r.outcome() == SyncOutcome.SKIPPED_ALREADY_RUNNING).count());
        });
        count("sync", summary == null ? "skipped" : summary.failed() > 0 ? "with-failures" : "ok");
        if (summary == null) {
            log.info("Scheduled sync skipped: another run holds the lock");
        } else {
            log.info("Scheduled sync: {} binding(s), {} succeeded, {} partial, {} failed", summary.bindings(), summary.succeeded(),
                    summary.partial(), summary.failed());
        }
        return summary;
    }

    /** @return what was deleted, or {@code null} if another run held the lock */
    public RetentionSummary runRetention() {
        RetentionSummary summary = lock.runExclusively(RETENTION_LOCK, () -> {
            Instant now = clock.instant();
            int runs = syncRuns.purgeFinishedBefore(now.minus(properties.retention().syncRuns()));
            completedPublications.deletePublicationsOlderThan(properties.retention().eventPublications());
            return new RetentionSummary(runs);
        });
        count("retention", summary == null ? "skipped" : "ok");
        if (summary != null) {
            log.info("Retention: deleted {} finished sync run(s) and delivered event publications older than {}", summary.syncRunsDeleted(),
                    properties.retention().eventPublications());
        }
        return summary;
    }

    private void count(String job, String result) {
        meters.counter("jobs.scheduled.runs", "job", job, "result", result).increment();
    }
}
