package com.jobsapply.platform.employer.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmployerRepository extends JpaRepository<Employer, UUID>, JpaSpecificationExecutor<Employer> {

    Optional<Employer> findByKey(String key);

    List<Employer> findByKeyIn(Collection<String> keys);
}
