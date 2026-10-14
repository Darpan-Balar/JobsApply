package com.jobsapply.platform.employer.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerSourceRepository extends JpaRepository<EmployerSource, UUID> {

    List<EmployerSource> findByEmployerId(UUID employerId);

    List<EmployerSource> findByEmployerIdIn(Collection<UUID> employerIds);

    Optional<EmployerSource> findByJobSourceIdAndExternalRef(UUID jobSourceId, String externalRef);
}
