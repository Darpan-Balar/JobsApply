package com.jobsapply.platform.matching;

/** Outcome of evaluating many postings. */
public record MatchRunReport(
        String candidateVersion, String roleProfile, int evaluated, int created, int updated, int unchanged,
        int eligible, int notEligible, int unknown, int shortlisted, int excludedByPreference) {}
