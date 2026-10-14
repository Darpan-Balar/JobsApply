package com.jobsapply.platform.preparation.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationPackageRepository extends JpaRepository<ApplicationPackage, UUID> {

    List<ApplicationPackage> findByApplicationIdOrderByVersionNoDesc(UUID applicationId);

    @Query("select max(p.versionNo) from ApplicationPackage p where p.applicationId = :applicationId")
    Optional<Integer> findMaxVersionNo(UUID applicationId);
}
