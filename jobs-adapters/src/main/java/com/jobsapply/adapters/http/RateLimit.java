package com.jobsapply.adapters.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/** At most {@code permits} requests per {@code per} to one host. */
public record RateLimit(@NotNull @Min(1) @Max(1000) Integer permits, @NotNull Duration per) {

    public RateLimit {
        if (per != null && (per.isNegative() || per.isZero())) {
            throw new IllegalArgumentException("rateLimit.per must be positive");
        }
    }

    public static RateLimit perSecond(int permits) {
        return new RateLimit(permits, Duration.ofSeconds(1));
    }

    Duration interval() {
        return per.dividedBy(permits);
    }
}
