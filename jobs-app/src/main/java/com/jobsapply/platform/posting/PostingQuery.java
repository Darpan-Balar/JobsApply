package com.jobsapply.platform.posting;

import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import java.time.Instant;
import lombok.Builder;

/** Posting search criteria; {@code null} means "any". */
@Builder
public record PostingQuery(
        String employerKey,
        Availability availability,
        WorkplaceType workplaceType,
        EmploymentType employmentType,
        Seniority seniority,
        String countryCode,
        String text,
        Boolean stale,
        Instant seenSince) {}
