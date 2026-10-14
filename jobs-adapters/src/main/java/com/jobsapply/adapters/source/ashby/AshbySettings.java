package com.jobsapply.adapters.source.ashby;

import com.jobsapply.adapters.http.HttpSourceSettings;
import com.jobsapply.adapters.http.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;

public record AshbySettings(
        Duration requestTimeout,
        @Min(0) @Max(5) Integer maxRetries,
        @Valid RateLimit rateLimit,
        Boolean includeCompensation) implements HttpSourceSettings {

    public AshbySettings {
        requestTimeout = HttpSourceSettings.timeoutOrDefault(requestTimeout);
        maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        rateLimit = rateLimit == null ? DEFAULT_RATE_LIMIT : rateLimit;
        includeCompensation = includeCompensation == null || includeCompensation;
    }
}
