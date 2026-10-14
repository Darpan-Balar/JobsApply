package com.jobsapply.platform.matching;

import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.matching.MatchResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param shortlisted eligibility not NOT_ELIGIBLE and score at or above the role's shortlist threshold
 */
public record MatchView(
        UUID id,
        UUID postingId,
        String employerKey,
        String employerName,
        String postingTitle,
        Availability postingAvailability,
        String candidateVersion,
        String roleProfileKey,
        int roleVersionNo,
        String strategy,
        BigDecimal score,
        Eligibility eligibility,
        boolean shortlisted,
        Instant evaluatedAt,
        MatchResult result) {}
