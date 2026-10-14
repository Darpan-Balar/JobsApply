package com.jobsapply.spi.matching;

import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.util.UUID;

public record MatchContext(
        JobSnapshot job, UUID candidateProfileVersionId, CandidateSnapshot candidate, RoleProfileDefinition role) {}
