package com.jobsapply.platform.tracking.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findAllByOrderByDueOnAsc();

    List<FollowUp> findByDoneAtIsNullOrderByDueOnAsc();

    List<FollowUp> findByApplicationIdOrderByDueOnAsc(UUID applicationId);

    long countByDoneAtIsNullAndDueOnBefore(LocalDate day);
}
