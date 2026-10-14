package com.jobsapply.platform.posting;

import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models of postings. */
public final class PostingViews {

    private PostingViews() {}

    /** Present only when the source published it. */
    public record Salary(BigDecimal min, BigDecimal max, String currency, CompensationPeriod period) {}

    public record Location(String rawText, String city, String region, String countryCode, boolean remote) {}

    public record Summary(
            UUID id,
            String employerKey,
            String employerName,
            String title,
            Seniority seniority,
            EmploymentType employmentType,
            WorkplaceType workplaceType,
            Salary salary,
            Availability availability,
            boolean stale,
            Instant firstSeenAt,
            Instant lastSeenAt,
            List<Location> locations) {}

    public record Listing(
            UUID id,
            String sourceKey,
            String externalId,
            String listingUrl,
            String applicationUrl,
            Instant publishedAt,
            Instant sourceUpdatedAt,
            Availability availability,
            String availabilityEvidence,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant lastCheckedAt) {}

    public record Detail(Summary summary, String descriptionText, List<Listing> listings) {}
}
