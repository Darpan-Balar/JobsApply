package com.jobsapply.platform.source.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSourceRepository extends JpaRepository<JobSource, UUID> {

    Optional<JobSource> findByKey(String key);
}
