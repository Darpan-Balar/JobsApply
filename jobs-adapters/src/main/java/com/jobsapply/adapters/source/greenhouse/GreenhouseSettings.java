package com.jobsapply.adapters.source.greenhouse;

import com.jobsapply.adapters.http.HttpSourceSettings;
import com.jobsapply.adapters.http.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;

public record GreenhouseSettings(Duration requestTimeout, @Min(0) @Max(5) Integer maxRetries, @Valid RateLimit rateLimit)
        implements HttpSourceSettings {

    public GreenhouseSettings {
        requestTimeout = HttpSourceSettings.timeoutOrDefault(requestTimeout);
        maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        rateLimit = rateLimit == null ? DEFAULT_RATE_LIMIT : rateLimit;
    }
}
