package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.application.ApplicationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Queries are restricted to the current user by Hibernate's tenant filter ({@code @TenantId}). */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    @Query("select a from Application a where a.jobPostingId = :postingId and a.candidateProfileId = :candidateProfileId and a.status not in :closed")
    Optional<Application> findOpen(UUID postingId, UUID candidateProfileId, Collection<ApplicationStatus> closed);

    List<Application> findAllByOrderByStatusChangedAtDesc();
}
