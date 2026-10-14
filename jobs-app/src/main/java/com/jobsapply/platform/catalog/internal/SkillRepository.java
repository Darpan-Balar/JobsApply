package com.jobsapply.platform.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    @Query("select distinct s from Skill s left join fetch s.aliases where s.key = :key")
    Optional<Skill> findByKey(String key);

    @Query("select distinct s from Skill s left join fetch s.aliases")
    List<Skill> findAllWithAliases();
}
