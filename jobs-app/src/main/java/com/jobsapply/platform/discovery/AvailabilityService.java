package com.jobsapply.platform.discovery;

import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.posting.ListingIngestService;
import com.jobsapply.platform.posting.ListingRef;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJobRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Rechecks whether a posting is still open, directly at its sources — required before preparing an application.
 * Sources with a single-job endpoint are asked about the job; others (complete listings, e.g. Ashby) are re-synced.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ListingIngestService ingest;
    private final EmployerService employers;
    private final PluginRegistry<JobSourceAdapter> adapters;
    private final SyncService sync;

    public record ListingCheck(String sourceKey, String externalId, String method, Availability availability, String detail) {}

    public record AvailabilityReport(UUID postingId, Availability availability, List<ListingCheck> checks) {}

    public AvailabilityReport recheck(UUID postingId) {
        List<ListingCheck> checks = new ArrayList<>();
        for (ListingRef listing : ingest.listingsOf(postingId)) {
            SyncTarget target = employers.syncTarget(listing.employerSourceId()).orElseThrow();
            JobSourceAdapter adapter = adapters.find(target.adapterId()).orElse(null);
            if (adapter == null) {
                checks.add(new ListingCheck(target.sourceKey(), listing.externalId(), "none", listing.availability(), "adapter not installed"));
            } else if (adapter.descriptor().supports(Capability.AVAILABILITY_CHECK)) {
                SourceBinding binding = new SourceBinding(target.sourceKey(), target.externalRef(), target.settings());
                switch (adapter.checkAvailability(new SourceJobRef(binding, listing.externalId()))) {
                    case PluginResult.Ok<AvailabilityObservation> ok -> {
                        ingest.applyObservation(listing.listingId(), ok.value());
                        checks.add(new ListingCheck(target.sourceKey(), listing.externalId(), "job endpoint",
                                ok.value().availability(), ok.value().evidence()));
                    }
                    case PluginResult.Failed<AvailabilityObservation> failed -> checks.add(new ListingCheck(target.sourceKey(),
                            listing.externalId(), "job endpoint", Availability.UNKNOWN, failed.kind() + ": " + failed.message()));
                    case PluginResult.Unsupported<AvailabilityObservation> unsupported -> { }
                }
            } else {
                SyncReport report = sync.sync(target, SyncTrigger.MANUAL, false);
                Availability after = ingest.listingsOf(postingId).stream()
                        .filter(l -> l.listingId().equals(listing.listingId())).findFirst().map(ListingRef::availability)
                        .orElse(Availability.UNKNOWN);
                checks.add(new ListingCheck(target.sourceKey(), listing.externalId(), "listing re-sync", after,
                        report.outcome() + (report.errors().isEmpty() ? "" : ": " + report.errors().getFirst())));
            }
        }
        return new AvailabilityReport(postingId, ingest.postingAvailability(postingId), checks);
    }
}
