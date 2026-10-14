package com.jobsapply.platform.roleprofile.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleProfileVersionRepository extends JpaRepository<RoleProfileVersion, UUID> {

    Optional<RoleProfileVersion> findByRoleProfileIdAndContentHash(UUID roleProfileId, String contentHash);

    @Query("select max(v.versionNo) from RoleProfileVersion v where v.roleProfileId = :roleProfileId")
    Optional<Integer> findMaxVersionNo(UUID roleProfileId);
}
