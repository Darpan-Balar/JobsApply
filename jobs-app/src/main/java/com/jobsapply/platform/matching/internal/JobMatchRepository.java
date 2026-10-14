package com.jobsapply.platform.matching.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {}
