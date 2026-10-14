package com.jobsapply.adapters.http;

import com.jobsapply.spi.common.PluginSettings;
import java.time.Duration;

/** Settings every HTTP-based source adapter supports. */
public interface HttpSourceSettings extends PluginSettings {

    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    int DEFAULT_MAX_RETRIES = 2;
    RateLimit DEFAULT_RATE_LIMIT = RateLimit.perSecond(4);

    Duration requestTimeout();

    Integer maxRetries();

    RateLimit rateLimit();

    static Duration timeoutOrDefault(Duration timeout) {
        if (timeout == null) {
            return DEFAULT_TIMEOUT;
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("requestTimeout must be between 0 and 2 minutes");
        }
        return timeout;
    }
}
