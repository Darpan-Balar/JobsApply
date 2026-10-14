package com.jobsapply.platform.employer.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepository extends JpaRepository<Employer, UUID> {

    Optional<Employer> findByKey(String key);
}
