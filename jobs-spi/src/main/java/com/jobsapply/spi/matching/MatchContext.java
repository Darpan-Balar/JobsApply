package com.jobsapply.spi.matching;

import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.time.Instant;
import java.util.UUID;

/** @param evaluatedAt the evaluation instant (plugins must not read the system clock) */
public record MatchContext(
        JobSnapshot job, UUID candidateProfileVersionId, CandidateSnapshot candidate, RoleProfileDefinition role, Instant evaluatedAt) {}
