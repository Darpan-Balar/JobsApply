package com.jobsapply.platform.matching.internal;

import com.jobsapply.spi.common.Eligibility;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Restricted to the current user by Hibernate's tenant filter. */
public interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {

    Optional<JobMatch> findByJobPostingIdAndCandidateProfileVersionIdAndRoleProfileVersionIdAndStrategyKeyAndStrategyVersion(
            UUID jobPostingId, UUID candidateProfileVersionId, UUID roleProfileVersionId, String strategyKey, int strategyVersion);

    @Query("""
            select m from JobMatch m
            where m.candidateProfileVersionId = :candidateVersionId and m.roleProfileVersionId = :roleVersionId
              and (:eligibility is null or m.eligibility = :eligibility)
              and (:minScore is null or m.score >= :minScore)
            order by m.score desc nulls last, m.evaluatedAt desc
            """)
    Page<JobMatch> search(UUID candidateVersionId, UUID roleVersionId, Eligibility eligibility, BigDecimal minScore, Pageable pageable);
}
