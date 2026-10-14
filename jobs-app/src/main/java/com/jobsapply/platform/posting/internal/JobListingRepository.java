package com.jobsapply.platform.posting.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobListingRepository extends JpaRepository<JobListing, UUID> {}
