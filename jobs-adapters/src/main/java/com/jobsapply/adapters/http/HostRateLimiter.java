package com.jobsapply.adapters.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Smooth per-host rate limiter: requests to a host are spaced at least {@code per / permits} apart, across all
 * bindings and threads using that host. Thread-safe: each caller reserves its slot atomically, then waits for it.
 */
public final class HostRateLimiter {

    private final Clock clock;
    private final Sleeper sleeper;
    private final ConcurrentMap<String, Instant> nextSlot = new ConcurrentHashMap<>();

    public HostRateLimiter(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public void acquire(String host, RateLimit limit) throws InterruptedException {
        Duration interval = limit.interval();
        Instant now = clock.instant();
        Instant[] reserved = new Instant[1];
        // One atomic step: take the earliest free slot for this host and push the next free slot one interval later.
        nextSlot.compute(host, (h, next) -> {
            Instant start = next == null || next.isBefore(now) ? now : next;
            reserved[0] = start;
            return start.plus(interval);
        });
        sleeper.sleep(Duration.between(now, reserved[0]));
    }
}
