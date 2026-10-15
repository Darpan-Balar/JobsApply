package com.jobsapply.platform.operations;

import com.jobsapply.platform.discovery.SyncRunQueryService;
import com.jobsapply.platform.discovery.SyncRunView;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.operations.internal.ScheduledJobs;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** What an administrator needs to know about the platform itself (no personal data). */
@Service
@RequiredArgsConstructor
public class SystemService {

    private final OperationsProperties properties;
    private final ScheduledJobs jobs;
    private final SyncRunQueryService syncRuns;
    private final JdbcTemplate jdbc;

    /**
     * @param schemaVersion the latest applied database migration
     * @param pendingEvents stored events not delivered yet (e.g. audit entries waiting to be written)
     * @param lastScheduledSync start of the most recent scheduled sync run, or {@code null}
     */
    public record SystemView(String schemaVersion, boolean scheduledSyncEnabled, String syncSchedule, Instant lastScheduledSync,
            boolean retentionEnabled, String retentionSchedule, String syncRunRetention, long pendingEvents) {}

    public record JobResult(boolean ran, Object summary) {}

    public SystemView view() {
        String version = jdbc.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class);
        Long pending = jdbc.queryForObject("select count(*) from event_publication where completion_date is null", Long.class);
        List<SyncRunView> recent = syncRuns.recent(null, null, 50);
        Instant lastScheduled = recent.stream().filter(r -> r.trigger() == SyncTrigger.SCHEDULED).map(SyncRunView::startedAt).findFirst()
                .orElse(null);
        return new SystemView(version, properties.sync().enabled(), properties.sync().cron(), lastScheduled, properties.retention().enabled(),
                properties.retention().cron(), properties.retention().syncRuns().toString(), pending == null ? 0 : pending);
    }

    /** Runs the scheduled synchronization now (same lock: refused if one is already running). */
    public JobResult syncNow() {
        ScheduledJobs.SyncSummary summary = jobs.runSync();
        return new JobResult(summary != null, summary);
    }

    public JobResult retentionNow() {
        ScheduledJobs.RetentionSummary summary = jobs.runRetention();
        return new JobResult(summary != null, summary);
    }
}
