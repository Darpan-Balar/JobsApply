package com.jobsapply.adapters.http;

import java.time.Duration;

/** Waiting, abstracted so retry and rate-limit tests run instantly. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    Sleeper SYSTEM = duration -> {
        if (!duration.isNegative() && !duration.isZero()) {
            Thread.sleep(duration);
        }
    };
}
