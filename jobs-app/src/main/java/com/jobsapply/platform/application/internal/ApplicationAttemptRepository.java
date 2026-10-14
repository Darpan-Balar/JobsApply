package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.application.AttemptOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAttemptRepository extends JpaRepository<ApplicationAttempt, UUID> {

    Optional<ApplicationAttempt> findByIdempotencyKey(String idempotencyKey);

    Optional<ApplicationAttempt> findFirstByApplicationIdAndOutcome(UUID applicationId, AttemptOutcome outcome);

    List<ApplicationAttempt> findByApplicationIdOrderByStartedAtDesc(UUID applicationId);
}
