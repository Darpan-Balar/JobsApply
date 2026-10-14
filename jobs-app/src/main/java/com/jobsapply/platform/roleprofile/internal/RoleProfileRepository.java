package com.jobsapply.platform.roleprofile.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every finder is explicitly scoped to "system template" and/or "owned by :owner" (ADR-0002). */
public interface RoleProfileRepository extends JpaRepository<RoleProfile, UUID> {

    @Query("select p from RoleProfile p where p.key = :key and p.ownerUserId is null")
    Optional<RoleProfile> findTemplate(String key);

    @Query("select p from RoleProfile p where p.key = :key and p.ownerUserId = :owner")
    Optional<RoleProfile> findOwned(UUID owner, String key);

    @Query("select p from RoleProfile p where (p.ownerUserId = :owner or p.ownerUserId is null) and p.archivedAt is null "
            + "order by p.key")
    List<RoleProfile> findVisible(UUID owner);
}
