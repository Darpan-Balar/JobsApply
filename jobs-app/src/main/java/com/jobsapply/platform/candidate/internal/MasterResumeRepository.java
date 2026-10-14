package com.jobsapply.platform.candidate.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterResumeRepository extends JpaRepository<MasterResume, UUID> {

    List<MasterResume> findByCandidateProfileIdOrderByUploadedAtDesc(UUID candidateProfileId);
}
