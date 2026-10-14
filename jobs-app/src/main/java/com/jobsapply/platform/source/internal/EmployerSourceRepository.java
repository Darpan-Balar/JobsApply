package com.jobsapply.platform.source.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerSourceRepository extends JpaRepository<EmployerSource, UUID> {}
