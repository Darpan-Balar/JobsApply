package com.jobsapply.platform.discovery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jobs.discovery.*}.
 *
 * @param pageSize requested page size (adapters may use their own)
 * @param maxPages safety cap per run; hitting it makes the run PARTIAL and the listing incomplete
 * @param maxAvailabilityChecks per run, for listings missing from an incomplete listing
 */
@Validated
@ConfigurationProperties("jobs.discovery")
public record DiscoveryProperties(
        @Min(1) @Max(1000) Integer pageSize, @Min(1) @Max(10_000) Integer maxPages, @Min(0) @Max(10_000) Integer maxAvailabilityChecks) {

    public DiscoveryProperties {
        pageSize = pageSize == null ? 100 : pageSize;
        maxPages = maxPages == null ? 100 : maxPages;
        maxAvailabilityChecks = maxAvailabilityChecks == null ? 50 : maxAvailabilityChecks;
    }
}
