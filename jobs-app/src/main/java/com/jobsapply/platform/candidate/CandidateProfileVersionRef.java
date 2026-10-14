package com.jobsapply.platform.candidate;

import java.util.UUID;

public record CandidateProfileVersionRef(UUID candidateProfileId, UUID versionId, int versionNo, String contentHash) {}
