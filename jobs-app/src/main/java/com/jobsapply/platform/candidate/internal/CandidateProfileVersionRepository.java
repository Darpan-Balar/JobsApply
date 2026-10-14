package com.jobsapply.platform.candidate.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CandidateProfileVersionRepository extends JpaRepository<CandidateProfileVersion, UUID> {

    @Query("select max(v.versionNo) from CandidateProfileVersion v where v.candidateProfileId = :profileId")
    Optional<Integer> findMaxVersionNo(UUID profileId);

    List<CandidateProfileVersion> findByCandidateProfileIdOrderByVersionNoDesc(UUID profileId);
}
