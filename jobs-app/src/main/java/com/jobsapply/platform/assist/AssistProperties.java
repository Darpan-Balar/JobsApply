package com.jobsapply.platform.assist;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jobs.assist.*}.
 *
 * @param adapter id of the application adapter plugin to use
 * @param maxObserve the longest a single "wait for the confirmation" call may block
 */
@ConfigurationProperties("jobs.assist")
public record AssistProperties(String adapter, Duration maxObserve) {

    public AssistProperties {
        adapter = adapter == null || adapter.isBlank() ? "playwright-browser" : adapter;
        maxObserve = Objects.requireNonNullElse(maxObserve, Duration.ofSeconds(120));
    }
}
