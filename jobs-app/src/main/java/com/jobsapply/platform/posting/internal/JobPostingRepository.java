package com.jobsapply.platform.posting.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {}
