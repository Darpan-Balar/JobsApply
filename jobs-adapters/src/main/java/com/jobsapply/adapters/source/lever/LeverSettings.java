package com.jobsapply.adapters.source.lever;

import com.jobsapply.adapters.http.HttpSourceSettings;
import com.jobsapply.adapters.http.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;

/**
 * @param region {@code GLOBAL} ({@code api.lever.co}) or {@code EU} ({@code api.eu.lever.co})
 * @param pageSize postings per request; a board that fits in one page is a complete listing
 */
public record LeverSettings(
        Duration requestTimeout,
        @Min(0) @Max(5) Integer maxRetries,
        @Valid RateLimit rateLimit,
        Region region,
        @Min(1) @Max(1000) Integer pageSize) implements HttpSourceSettings {

    public enum Region {
        GLOBAL("https://api.lever.co/v0/postings/"),
        EU("https://api.eu.lever.co/v0/postings/");

        final String base;

        Region(String base) {
            this.base = base;
        }
    }

    public LeverSettings {
        requestTimeout = HttpSourceSettings.timeoutOrDefault(requestTimeout);
        maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        rateLimit = rateLimit == null ? DEFAULT_RATE_LIMIT : rateLimit;
        region = region == null ? Region.GLOBAL : region;
        pageSize = pageSize == null ? 250 : pageSize;
    }
}
