package com.jobsapply.adapters.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.Fixtures;
import com.jobsapply.adapters.RecordingSleeper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HostRateLimiterTest {

    @Test
    void spacesRequestsPerHostAndKeepsHostsIndependent() throws InterruptedException {
        RecordingSleeper sleeper = new RecordingSleeper();
        HostRateLimiter limiter = new HostRateLimiter(Fixtures.CLOCK, sleeper); // clock frozen: nothing elapses
        RateLimit twoPerSecond = RateLimit.perSecond(2);

        limiter.acquire("a.example", twoPerSecond);
        limiter.acquire("a.example", twoPerSecond);
        limiter.acquire("a.example", twoPerSecond);
        limiter.acquire("b.example", twoPerSecond);

        assertThat(sleeper.sleeps).containsExactly(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(1000), Duration.ZERO);
    }
}
