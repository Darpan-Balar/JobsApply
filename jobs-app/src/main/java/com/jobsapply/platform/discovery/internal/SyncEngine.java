package com.jobsapply.platform.discovery.internal;

import com.jobsapply.platform.discovery.DiscoveryProperties;
import com.jobsapply.platform.discovery.SyncCounts;
import com.jobsapply.platform.discovery.SyncOutcome;
import com.jobsapply.platform.discovery.SyncReport;
import com.jobsapply.platform.discovery.SyncRunStatus;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.discovery.internal.SyncRunRecorder.ErrorRecord;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.posting.IngestOutcome;
import com.jobsapply.platform.posting.ListingIngestService;
import com.jobsapply.platform.posting.ListingRef;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Synchronizes one binding:
 *
 * <ol>
 *   <li>claim the binding (database single-flight; a concurrent run is reported as skipped);
 *   <li>fetch every page — network I/O happens outside any transaction;
 *   <li>for listings absent from an <em>incomplete</em> listing, ask the source directly (bounded);
 *   <li>apply: idempotent upserts; closures only from a complete listing or an authoritative observation;
 *   <li>record the run, its errors, binding health and metrics.
 * </ol>
 *
 * A failed first page changes nothing. A dry run performs every step but rolls back the changes (run history is kept).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncEngine {

    private final PluginRegistry<JobSourceAdapter> adapters;
    private final ListingIngestService ingest;
    private final EmployerService employers;
    private final SyncRunRecorder recorder;
    private final TransactionTemplate transactions;
    private final DiscoveryProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    private record Fetch(Map<String, SourceJob> jobs, Set<String> problemRefs, boolean complete, boolean nothingFetched) {}

    private static final class Counter {
        int discovered, inserted, updated, unchanged, closed, failed;

        SyncCounts snapshot() {
            return new SyncCounts(discovered, inserted, updated, unchanged, closed, failed);
        }
    }

    public SyncReport sync(SyncTarget target, SyncTrigger trigger, boolean dryRun) {
        Instant started = clock.instant();
        Optional<UUID> runId = recorder.start(target, trigger, dryRun, started);
        if (runId.isEmpty()) {
            return report(null, target, dryRun, SyncOutcome.SKIPPED_ALREADY_RUNNING, SyncCounts.zero(),
                    List.of("another sync of this binding is in progress"));
        }
        Timer.Sample timer = Timer.start(meters);
        List<ErrorRecord> problems = new ArrayList<>();
        Counter counter = new Counter();
        SyncOutcome outcome;
        try {
            outcome = run(target, dryRun, problems, counter);
        } catch (RuntimeException e) {
            log.warn("Sync of {}/{} failed unexpectedly", target.sourceKey(), target.externalRef(), e);
            problems.add(new ErrorRecord(FailureKind.PERMANENT, null, "unexpected error: " + e.getClass().getSimpleName()));
            outcome = SyncOutcome.FAILED;
        }
        Instant finished = clock.instant();
        SyncRunStatus status = switch (outcome) {
            case SUCCEEDED -> SyncRunStatus.SUCCEEDED;
            case PARTIAL -> SyncRunStatus.PARTIAL;
            default -> SyncRunStatus.FAILED;
        };
        recorder.finish(runId.get(), status, counter.snapshot(), problems, finished);
        if (!dryRun) {
            employers.recordSyncOutcome(target.employerSourceId(), outcome != SyncOutcome.FAILED, finished,
                    problems.isEmpty() ? null : problems.getFirst().message());
            recordMetrics(target, counter);
        }
        timer.stop(meters.timer("jobs.discovery.sync", "source", target.sourceKey(), "outcome", outcome.name(), "dryRun", String.valueOf(dryRun)));
        return report(runId.get(), target, dryRun, outcome, counter.snapshot(), problems.stream().map(ErrorRecord::message).toList());
    }

    private SyncOutcome run(SyncTarget target, boolean dryRun, List<ErrorRecord> problems, Counter counter) {
        JobSourceAdapter adapter = adapters.find(target.adapterId()).orElse(null);
        if (adapter == null || !adapter.descriptor().supports(Capability.LIST_JOBS)) {
            problems.add(new ErrorRecord(FailureKind.PERMANENT, null, "adapter '" + target.adapterId() + "' is not installed or cannot list jobs"));
            return SyncOutcome.FAILED;
        }
        SourceBinding binding = new SourceBinding(target.sourceKey(), target.externalRef(), target.settings());
        Fetch fetch = fetchAll(adapter, binding, problems);
        if (fetch.nothingFetched()) {
            return SyncOutcome.FAILED; // availability untouched: a failed request is not evidence of closure
        }
        counter.discovered = fetch.jobs().size() + fetch.problemRefs().size();
        counter.failed = fetch.problemRefs().size();

        Set<String> seen = new HashSet<>(fetch.jobs().keySet());
        seen.addAll(fetch.problemRefs());
        boolean canCloseAbsent = fetch.complete();

        List<Map.Entry<UUID, AvailabilityObservation>> observations = new ArrayList<>();
        if (!canCloseAbsent && adapter.descriptor().supports(Capability.AVAILABILITY_CHECK)) {
            List<ListingRef> unseen = ingest.notClosedAndUnseen(target.employerSourceId(), seen);
            if (unseen.size() > properties.maxAvailabilityChecks()) {
                problems.add(new ErrorRecord(FailureKind.TRANSIENT, null, (unseen.size() - properties.maxAvailabilityChecks())
                        + " listing(s) missing from an incomplete listing were not rechecked (per-run limit)"));
            }
            for (ListingRef listing : unseen.stream().limit(properties.maxAvailabilityChecks()).toList()) {
                switch (adapter.checkAvailability(new SourceJobRef(binding, listing.externalId()))) {
                    case PluginResult.Ok<AvailabilityObservation> ok -> observations.add(Map.entry(listing.listingId(), ok.value()));
                    case PluginResult.Failed<AvailabilityObservation> failed ->
                            problems.add(new ErrorRecord(failed.kind(), listing.externalId(), "availability check: " + failed.message()));
                    case PluginResult.Unsupported<AvailabilityObservation> unsupported -> { }
                }
            }
        }

        Runnable apply = () -> apply(target, fetch, seen, canCloseAbsent, observations, problems, counter);
        if (dryRun) {
            transactions.executeWithoutResult(status -> {
                apply.run();
                status.setRollbackOnly();
            });
        } else {
            apply.run();
        }
        return problems.isEmpty() && counter.failed == 0 ? SyncOutcome.SUCCEEDED : SyncOutcome.PARTIAL;
    }

    private void apply(SyncTarget target, Fetch fetch, Set<String> seen, boolean canCloseAbsent,
            List<Map.Entry<UUID, AvailabilityObservation>> observations, List<ErrorRecord> problems, Counter counter) {
        Instant now = clock.instant();
        for (SourceJob job : fetch.jobs().values()) {
            try {
                IngestOutcome outcome = ingest.ingest(target.employerSourceId(), target.employerId(), job, now);
                switch (outcome) {
                    case INSERTED -> counter.inserted++;
                    case UPDATED -> counter.updated++;
                    case UNCHANGED -> counter.unchanged++;
                }
            } catch (RuntimeException e) {
                counter.failed++;
                problems.add(new ErrorRecord(FailureKind.PERMANENT, job.externalId(), "could not store: " + e.getClass().getSimpleName()));
            }
        }
        if (canCloseAbsent) {
            counter.closed += ingest.closeAbsent(target.employerSourceId(), seen, now,
                    "absent from the complete " + target.sourceKey() + " listing at " + now);
        }
        for (Map.Entry<UUID, AvailabilityObservation> observation : observations) {
            if (ingest.applyObservation(observation.getKey(), observation.getValue())) {
                if (observation.getValue().availability() == Availability.CLOSED) {
                    counter.closed++;
                } else {
                    counter.updated++;
                }
            }
        }
    }

    private Fetch fetchAll(JobSourceAdapter adapter, SourceBinding binding, List<ErrorRecord> problems) {
        Map<String, SourceJob> jobs = new LinkedHashMap<>();
        Set<String> problemRefs = new HashSet<>();
        String cursor = null;
        int pages = 0;
        boolean complete = false;
        boolean failed = false;
        boolean unidentifiedProblem = false;
        do {
            PluginResult<DiscoveryPage> result = adapter.discoverJobs(new DiscoveryRequest(binding, cursor, properties.pageSize(), null));
            if (!(result instanceof PluginResult.Ok<DiscoveryPage> ok)) {
                String message = result instanceof PluginResult.Failed<DiscoveryPage> f ? f.message() : "listing unsupported";
                FailureKind kind = result instanceof PluginResult.Failed<DiscoveryPage> f ? f.kind() : FailureKind.PERMANENT;
                problems.add(new ErrorRecord(kind, null, (pages == 0 ? "listing failed: " : "page " + (pages + 1) + " failed: ") + message));
                failed = true;
                break;
            }
            DiscoveryPage page = ok.value();
            page.jobs().forEach(job -> jobs.putIfAbsent(job.externalId(), job));
            for (DiscoveryPage.ItemProblem problem : page.problems()) {
                problems.add(new ErrorRecord(FailureKind.PARSE, problem.externalRef(), "unparseable item: " + problem.message()));
                if (problem.externalRef() == null) {
                    unidentifiedProblem = true; // cannot tell which listing it is: never close on this listing
                } else {
                    problemRefs.add(problem.externalRef());
                }
            }
            complete = page.complete();
            pages++;
            if (page.nextCursor() != null && page.nextCursor().equals(cursor)) {
                problems.add(new ErrorRecord(FailureKind.PERMANENT, null, "adapter returned the same cursor twice; stopping"));
                failed = true;
                break;
            }
            cursor = page.nextCursor();
        } while (cursor != null && pages < properties.maxPages());
        if (cursor != null && !failed) {
            problems.add(new ErrorRecord(FailureKind.TRANSIENT, null, "page limit (" + properties.maxPages() + ") reached; listing incomplete"));
            failed = true;
        }
        boolean nothingFetched = failed && pages == 0;
        return new Fetch(jobs, problemRefs, complete && !failed && !unidentifiedProblem, nothingFetched);
    }

    private void recordMetrics(SyncTarget target, Counter counter) {
        String source = target.sourceKey();
        meters.counter("jobs.discovery.listings", "source", source, "outcome", "inserted").increment(counter.inserted);
        meters.counter("jobs.discovery.listings", "source", source, "outcome", "updated").increment(counter.updated);
        meters.counter("jobs.discovery.listings", "source", source, "outcome", "unchanged").increment(counter.unchanged);
        meters.counter("jobs.discovery.listings", "source", source, "outcome", "closed").increment(counter.closed);
        meters.counter("jobs.discovery.listings", "source", source, "outcome", "failed").increment(counter.failed);
    }

    private static SyncReport report(UUID runId, SyncTarget target, boolean dryRun, SyncOutcome outcome, SyncCounts counts, List<String> errors) {
        return new SyncReport(runId, target.employerKey(), target.sourceKey(), target.externalRef(), dryRun, outcome, counts, errors);
    }
}
