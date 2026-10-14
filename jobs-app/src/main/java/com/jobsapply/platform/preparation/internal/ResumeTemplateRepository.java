package com.jobsapply.platform.preparation.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResumeTemplateRepository extends JpaRepository<ResumeTemplate, UUID> {

    @Query("select t from ResumeTemplate t where t.key = :key and t.ownerUserId is null")
    Optional<ResumeTemplate> findSystem(String key);

    @Query("select t.key from ResumeTemplate t where t.ownerUserId is null")
    List<String> systemKeys();
}
