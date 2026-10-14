package com.jobsapply.platform.application.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubmissionApprovalRepository extends JpaRepository<SubmissionApproval, UUID> {

    @Query("select a from SubmissionApproval a where a.applicationId = :applicationId and a.consumedAt is null and a.revokedAt is null")
    Optional<SubmissionApproval> findActive(UUID applicationId);

    List<SubmissionApproval> findByApplicationIdOrderByApprovedAtDesc(UUID applicationId);
}
