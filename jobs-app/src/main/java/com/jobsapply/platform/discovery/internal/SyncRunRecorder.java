package com.jobsapply.platform.discovery.internal;

import com.jobsapply.platform.discovery.SyncCounts;
import com.jobsapply.platform.discovery.SyncRunStatus;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.spi.common.FailureKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes run history in its own transactions, so it is committed even when the run's work is rolled back (dry runs)
 * and visible while the run is in progress.
 */
@Component
class SyncRunRecorder {

    static final int MAX_STORED_ERRORS = 200;

    record ErrorRecord(FailureKind kind, String externalRef, String message) {}

    private final SyncRunRepository runs;
    private final SyncErrorRepository errors;
    private final TransactionTemplate requiresNew;

    SyncRunRecorder(SyncRunRepository runs, SyncErrorRepository errors, PlatformTransactionManager transactionManager) {
        this.runs = runs;
        this.errors = errors;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return the run id, or empty if another run for this binding is in progress (database single-flight) */
    Optional<UUID> start(SyncTarget target, SyncTrigger trigger, boolean dryRun, Instant now) {
        try {
            return Optional.ofNullable(requiresNew.execute(status ->
                    runs.saveAndFlush(SyncRun.start(target.jobSourceId(), target.employerSourceId(), trigger, dryRun, now)).getId()));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_sync_run_running_binding")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    void finish(UUID runId, SyncRunStatus status, SyncCounts counts, List<ErrorRecord> problems, Instant now) {
        requiresNew.executeWithoutResult(tx -> {
            String summary = problems.isEmpty() ? null : problems.size() + " problem(s); first: " + truncate(problems.getFirst().message(), 500);
            runs.findById(runId).orElseThrow().finish(status, now, counts, summary);
            problems.stream().limit(MAX_STORED_ERRORS).forEach(p -> errors.save(SyncError.builder()
                    .syncRunId(runId)
                    .kind(p.kind())
                    .externalRef(truncate(p.externalRef(), 200))
                    .message(truncate(p.message(), 4000))
                    .occurredAt(now)
                    .build()));
        });
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
