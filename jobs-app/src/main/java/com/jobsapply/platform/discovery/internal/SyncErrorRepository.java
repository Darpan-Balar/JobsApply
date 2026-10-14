package com.jobsapply.platform.discovery.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncErrorRepository extends JpaRepository<SyncError, UUID> {}
