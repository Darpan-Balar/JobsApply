package com.jobsapply.platform.discovery;

import java.util.List;
import java.util.UUID;

/** @param syncRunId {@code null} when skipped */
public record SyncReport(
        UUID syncRunId,
        String employerKey,
        String sourceKey,
        String externalRef,
        boolean dryRun,
        SyncOutcome outcome,
        SyncCounts counts,
        List<String> errors) {

    public SyncReport {
        errors = List.copyOf(errors);
    }
}
