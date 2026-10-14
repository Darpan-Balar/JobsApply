package com.jobsapply.platform.candidate;

import com.jobsapply.spi.profile.CandidateSnapshot;

public record CandidateProfileVersionView(CandidateProfileVersionRef ref, CandidateSnapshot snapshot) {}
