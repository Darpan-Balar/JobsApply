package com.jobsapply.platform.candidate;

import java.util.UUID;

public record CandidateProfileRef(UUID id, String key, String displayName) {}
