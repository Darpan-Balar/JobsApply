package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.posting.IngestOutcome;
import com.jobsapply.platform.posting.ListingRef;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.SourceJob;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementation of {@code ListingIngestService}; callers provide the transaction. */
@Component
@RequiredArgsConstructor
public class ListingIngestor {

    private final JobListingRepository listings;
    private final JobPostingRepository postings;
    private final JsonCodec json;

    public IngestOutcome ingest(UUID employerSourceId, UUID employerId, SourceJob job, Instant now) {
        String contentHash = json.contentHash(job);
        JobListing listing = listings.findByEmployerSourceIdAndExternalId(employerSourceId, job.externalId()).orElse(null);
        if (listing == null) {
            NormalizedJob normalized = JobNormalizer.normalize(employerId, job);
            JobPosting posting = postings.mergeCandidates(employerId, normalized.fingerprint(), employerSourceId, Availability.CLOSED)
                    .stream().findFirst()
                    .orElseGet(() -> postings.save(JobPosting.from(employerId, normalized, now)));
            posting.markSeen(now);
            JobListing created = JobListing.builder()
                    .employerSourceId(employerSourceId)
                    .jobPostingId(posting.getId())
                    .externalId(job.externalId())
                    .titleRaw(job.title())
                    .contentHash(contentHash)
                    .firstSeenAt(now)
                    .lastSeenAt(now)
                    .lastCheckedAt(now)
                    .availability(Availability.OPEN)
                    .availabilityEvidence("present in the source listing")
                    .build();
            created.replaceContent(job, json.write(job.locations()), json.write(job.sourceMetadata()), contentHash);
            listings.save(created);
            listings.flush();
            posting.deriveAvailability(listings.availabilitiesOf(posting.getId()));
            return IngestOutcome.INSERTED;
        }

        boolean reopened = listing.markSeen(now);
        boolean contentChanged = !listing.getContentHash().equals(contentHash);
        if (contentChanged) {
            listing.replaceContent(job, json.write(job.locations()), json.write(job.sourceMetadata()), contentHash);
        }
        JobPosting posting = postings.findById(listing.getJobPostingId()).orElseThrow();
        posting.markSeen(now);
        if (contentChanged && isAuthoritativeListing(listing)) {
            posting.refresh(JobNormalizer.normalize(employerId, job));
        }
        if (reopened) {
            listings.flush();
            posting.deriveAvailability(listings.availabilitiesOf(posting.getId()));
        }
        return contentChanged || reopened ? IngestOutcome.UPDATED : IngestOutcome.UNCHANGED;
    }

    public int closeAbsent(UUID employerSourceId, Set<String> seenExternalIds, Instant now, String evidence) {
        int closed = 0;
        for (JobListing listing : listings.findByEmployerSourceIdAndAvailabilityNot(employerSourceId, Availability.CLOSED)) {
            if (!seenExternalIds.contains(listing.getExternalId())
                    && listing.observe(Availability.CLOSED, true, evidence, now)) {
                closed++;
                listings.flush();
                rederive(listing.getJobPostingId());
            }
        }
        return closed;
    }

    public List<ListingRef> notClosedAndUnseen(UUID employerSourceId, Set<String> seenExternalIds) {
        return listings.findByEmployerSourceIdAndAvailabilityNot(employerSourceId, Availability.CLOSED).stream()
                .filter(l -> !seenExternalIds.contains(l.getExternalId()))
                .map(ListingIngestor::ref)
                .toList();
    }

    public boolean applyObservation(UUID listingId, AvailabilityObservation observation) {
        JobListing listing = listings.findById(listingId).orElseThrow(() -> new NotFoundException("Listing", listingId));
        boolean changed = listing.observe(observation.availability(), observation.authoritative(), observation.evidence(),
                observation.observedAt());
        if (changed) {
            listings.flush();
            rederive(listing.getJobPostingId());
        }
        return changed;
    }

    public List<ListingRef> listingsOf(UUID postingId) {
        return listings.findByJobPostingIdOrderByFirstSeenAtAsc(postingId).stream().map(ListingIngestor::ref).toList();
    }

    public Availability postingAvailability(UUID postingId) {
        return postings.findById(postingId).map(JobPosting::getAvailability)
                .orElseThrow(() -> new NotFoundException("Posting", postingId));
    }

    /** The oldest listing of a posting defines its canonical content (stable when duplicates from other sources join). */
    private boolean isAuthoritativeListing(JobListing listing) {
        return listings.earliestFirstSeen(listing.getJobPostingId())
                .map(earliest -> !listing.getFirstSeenAt().isAfter(earliest))
                .orElse(true);
    }

    private void rederive(UUID postingId) {
        postings.findById(postingId).ifPresent(p -> p.deriveAvailability(listings.availabilitiesOf(postingId)));
    }

    private static ListingRef ref(JobListing l) {
        return new ListingRef(l.getId(), l.getEmployerSourceId(), l.getExternalId(), l.getAvailability());
    }
}
