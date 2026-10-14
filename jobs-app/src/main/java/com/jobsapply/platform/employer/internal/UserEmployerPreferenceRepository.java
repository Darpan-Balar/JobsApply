package com.jobsapply.platform.employer.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Restricted to the current user by Hibernate's tenant filter. */
public interface UserEmployerPreferenceRepository extends JpaRepository<UserEmployerPreference, UUID> {

    Optional<UserEmployerPreference> findByEmployerId(UUID employerId);

    List<UserEmployerPreference> findAllByOrderByPriorityDesc();
}
