package com.jobsapply.spi.source;

import com.jobsapply.spi.common.Availability;
import java.time.Instant;

/**
 * @param authoritative {@code true} only when the provider itself stated the job's state (e.g. a documented 404 for a
 *     removed posting). Non-authoritative observations never close a job.
 * @param evidence short human-readable description of what was observed (status code, field value)
 */
public record AvailabilityObservation(
        Availability availability, boolean authoritative, String evidence, Instant observedAt) {}
