package com.jobsapply.platform.posting;

import com.jobsapply.platform.posting.internal.ListingIngestor;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.SourceJob;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API for writing discovered jobs. Every operation is idempotent: listings are keyed by (binding, external id)
 * and compared by content hash. Availability changes only on authoritative evidence.
 */
@Service
@RequiredArgsConstructor
public class ListingIngestService {

    private final ListingIngestor ingestor;

    @Transactional
    public IngestOutcome ingest(UUID employerSourceId, UUID employerId, SourceJob job, Instant now) {
        return ingestor.ingest(employerSourceId, employerId, job, now);
    }

    /**
     * Closes listings of this binding that are absent from a <em>complete</em> source listing. Never call this with an
     * incomplete or failed listing. @return the number of listings closed
     */
    @Transactional
    public int closeAbsent(UUID employerSourceId, Set<String> seenExternalIds, Instant now, String evidence) {
        return ingestor.closeAbsent(employerSourceId, seenExternalIds, now, evidence);
    }

    /** Listings of this binding that are not closed and were not in {@code seenExternalIds}. */
    @Transactional(readOnly = true)
    public List<ListingRef> notClosedAndUnseen(UUID employerSourceId, Set<String> seenExternalIds) {
        return ingestor.notClosedAndUnseen(employerSourceId, seenExternalIds);
    }

    /** @return whether the listing's availability changed */
    @Transactional
    public boolean applyObservation(UUID listingId, AvailabilityObservation observation) {
        return ingestor.applyObservation(listingId, observation);
    }

    @Transactional(readOnly = true)
    public List<ListingRef> listingsOf(UUID postingId) {
        return ingestor.listingsOf(postingId);
    }

    @Transactional(readOnly = true)
    public Availability postingAvailability(UUID postingId) {
        return ingestor.postingAvailability(postingId);
    }
}
