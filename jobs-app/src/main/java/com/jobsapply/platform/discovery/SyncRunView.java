package com.jobsapply.platform.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncRunView(
        UUID id,
        String sourceKey,
        String employerKey,
        String externalRef,
        SyncTrigger trigger,
        boolean dryRun,
        SyncRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        SyncCounts counts,
        String errorSummary,
        List<Error> errors) {

    public record Error(String kind, String externalRef, String message, Instant occurredAt) {}
}
