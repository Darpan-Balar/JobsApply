package com.jobsapply.platform.posting.internal;

import com.jobsapply.spi.common.Availability;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobListingRepository extends JpaRepository<JobListing, UUID> {

    Optional<JobListing> findByEmployerSourceIdAndExternalId(UUID employerSourceId, String externalId);

    List<JobListing> findByEmployerSourceIdAndAvailabilityNot(UUID employerSourceId, Availability availability);

    List<JobListing> findByJobPostingIdOrderByFirstSeenAtAsc(UUID jobPostingId);

    List<JobListing> findByJobPostingIdIn(Collection<UUID> postingIds);

    @Query("select l.availability from JobListing l where l.jobPostingId = :postingId")
    List<Availability> availabilitiesOf(UUID postingId);

    @Query("select min(l.firstSeenAt) from JobListing l where l.jobPostingId = :postingId")
    Optional<Instant> earliestFirstSeen(UUID postingId);
}
