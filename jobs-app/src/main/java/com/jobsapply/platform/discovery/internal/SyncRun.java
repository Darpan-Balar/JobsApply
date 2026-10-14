package com.jobsapply.platform.discovery.internal;

import com.jobsapply.platform.discovery.SyncCounts;
import com.jobsapply.platform.discovery.SyncRunStatus;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One synchronization of a source (optionally scoped to one employer binding). The database allows at most one
 * {@code RUNNING} run per binding / per whole source.
 */
@Entity
@Table(name = "sync_run")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SyncRun extends BaseEntity {

    @Column(name = "job_source_id", nullable = false, updatable = false)
    private UUID jobSourceId;

    @Column(name = "employer_source_id", updatable = false)
    private UUID employerSourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, updatable = false, length = 16)
    private SyncTrigger trigger;

    @Column(name = "dry_run", nullable = false, updatable = false)
    private boolean dryRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SyncRunStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "discovered_count", nullable = false)
    private int discoveredCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;

    @Column(name = "closed_count", nullable = false)
    private int closedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    public static SyncRun start(UUID jobSourceId, UUID employerSourceId, SyncTrigger trigger, boolean dryRun, Instant now) {
        return SyncRun.builder()
                .jobSourceId(Objects.requireNonNull(jobSourceId))
                .employerSourceId(employerSourceId)
                .trigger(Objects.requireNonNull(trigger))
                .dryRun(dryRun)
                .status(SyncRunStatus.RUNNING)
                .startedAt(Objects.requireNonNull(now))
                .build();
    }

    public void finish(SyncRunStatus outcome, Instant now, SyncCounts counts, String errorSummary) {
        if (status.isFinished()) {
            throw new IllegalStateException("Sync run already finished: " + status);
        }
        if (!outcome.isFinished()) {
            throw new IllegalArgumentException("Outcome must be a finished status");
        }
        this.status = outcome;
        this.finishedAt = now;
        this.discoveredCount = counts.discovered();
        this.insertedCount = counts.inserted();
        this.updatedCount = counts.updated();
        this.unchangedCount = counts.unchanged();
        this.closedCount = counts.closed();
        this.failedCount = counts.failed();
        this.errorSummary = errorSummary;
    }

    public SyncCounts counts() {
        return new SyncCounts(discoveredCount, insertedCount, updatedCount, unchangedCount, closedCount, failedCount);
    }
}
