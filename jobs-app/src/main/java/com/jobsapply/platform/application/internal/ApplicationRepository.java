package com.jobsapply.platform.application.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries are restricted to the current user by Hibernate's tenant filter ({@code @TenantId}). */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {}
