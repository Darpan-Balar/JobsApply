package com.jobsapply.platform.posting.internal;

import com.jobsapply.spi.common.Availability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    /**
     * Cross-source dedup candidates: same employer and fingerprint, not closed, and not already listed through this
     * binding (two identical openings on the same board stay separate postings).
     */
    @Query("""
            select p from JobPosting p
            where p.employerId = :employerId and p.dedupFingerprint = :fingerprint and p.availability <> :closed
              and not exists (select 1 from JobListing l where l.jobPostingId = p.id and l.employerSourceId = :employerSourceId)
            order by p.firstSeenAt asc
            """)
    List<JobPosting> mergeCandidates(UUID employerId, String fingerprint, UUID employerSourceId, Availability closed);
}
