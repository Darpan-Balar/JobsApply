package com.jobsapply.platform.tracking.internal;

import com.jobsapply.platform.tracking.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findAllByOrderByScheduledAtAsc();

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);

    List<Interview> findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            InterviewStatus status, Instant from, Instant to);
}
