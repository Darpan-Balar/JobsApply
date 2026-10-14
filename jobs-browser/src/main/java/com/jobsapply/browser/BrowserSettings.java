package com.jobsapply.browser;

import java.time.Duration;
import java.util.Objects;

/**
 * {@code jobs.browser.*}.
 *
 * @param headless {@code false} (the default) shows the window: the human must see and submit the form. Tests only.
 * @param channel an installed browser to use instead of Playwright's own build (e.g. {@code chrome}), or {@code null}
 * @param navigationTimeout how long opening the application page may take
 * @param actionTimeout how long a single fill action may take
 * @param launchTimeout how long starting the browser may take (the first start may download Playwright's browser build)
 */
public record BrowserSettings(boolean headless, String channel, Duration navigationTimeout, Duration actionTimeout,
        Duration launchTimeout) {

    public BrowserSettings {
        channel = channel == null || channel.isBlank() ? null : channel.strip();
        navigationTimeout = Objects.requireNonNullElse(navigationTimeout, Duration.ofSeconds(30));
        actionTimeout = Objects.requireNonNullElse(actionTimeout, Duration.ofSeconds(10));
        launchTimeout = Objects.requireNonNullElse(launchTimeout, Duration.ofMinutes(5));
    }

    public static BrowserSettings defaults() {
        return new BrowserSettings(false, null, null, null, null);
    }
}
