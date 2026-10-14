package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/** Normalized, read-only view of a canonical job posting handed to plugins. */
@Builder(toBuilder = true)
public record JobSnapshot(
        UUID postingId,
        String employerKey,
        String title,
        String titleNormalized,
        Seniority seniority,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        List<RawLocation> locations,
        Compensation compensation,
        String descriptionText,
        Instant publishedAt,
        /* Canonical skill keys the platform found in the title and description (catalog-based). */
        Set<String> skills) {

    public JobSnapshot {
        locations = Lists.immutable(locations);
        skills = skills == null ? Set.of() : Set.copyOf(skills);
    }
}
