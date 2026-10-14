package com.jobsapply.platform.candidate.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries are restricted to the current user by Hibernate's tenant filter ({@code @TenantId}). */
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByKey(String key);

    List<CandidateProfile> findAllByOrderByKey();
}
