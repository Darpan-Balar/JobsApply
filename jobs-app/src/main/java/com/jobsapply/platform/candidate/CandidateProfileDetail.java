package com.jobsapply.platform.candidate;

import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.Map;

/** A profile with its current version, and how many facts have each verification status. */
public record CandidateProfileDetail(
        CandidateProfileRef profile, CandidateProfileVersionRef currentVersion, CandidateSnapshot snapshot,
        Map<VerificationStatus, Long> factsByVerification) {}
