package com.jobsapply.browser;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registers the Playwright application adapter (ADR-0003 plugin mechanism). Disable with
 * {@code jobs.browser.enabled=false}. The browser itself starts only when the first assistance window opens.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "jobs.browser", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrowserAssistAutoConfiguration {

    @Bean(destroyMethod = "close")
    PlaywrightApplicationAdapter playwrightApplicationAdapter(Environment env, ObjectProvider<Clock> clock) {
        BrowserSettings settings = new BrowserSettings(
                env.getProperty("jobs.browser.headless", Boolean.class, false),
                env.getProperty("jobs.browser.channel"),
                env.getProperty("jobs.browser.navigation-timeout", Duration.class),
                env.getProperty("jobs.browser.action-timeout", Duration.class),
                env.getProperty("jobs.browser.launch-timeout", Duration.class));
        return new PlaywrightApplicationAdapter(settings, clock.getIfAvailable(Clock::systemUTC));
    }
}
