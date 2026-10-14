package com.jobsapply.platform.catalog.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobFamilyRepository extends JpaRepository<JobFamily, UUID> {

    Optional<JobFamily> findByKey(String key);
}
