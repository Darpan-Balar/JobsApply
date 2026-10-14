package com.jobsapply.adapters;

import com.jobsapply.adapters.http.HostRateLimiter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.adapters.http.JdkHttpTransport;
import com.jobsapply.adapters.http.Sleeper;
import com.jobsapply.adapters.source.ashby.AshbyJobBoardAdapter;
import com.jobsapply.adapters.source.greenhouse.GreenhouseJobBoardAdapter;
import com.jobsapply.adapters.source.lever.LeverPostingsAdapter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the built-in adapters through Spring Boot auto-configuration — the same mechanism a third-party plugin
 * JAR uses (ADR-0003). The platform's plugin registry validates them at startup.
 */
@AutoConfiguration
public class BuiltInAdaptersAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    HttpFetcher jobSourceHttpFetcher(ObjectProvider<Clock> clock) {
        return new HttpFetcher(new JdkHttpTransport(),
                new HostRateLimiter(clock.getIfAvailable(Clock::systemUTC), Sleeper.SYSTEM), Sleeper.SYSTEM);
    }

    @Bean
    GreenhouseJobBoardAdapter greenhouseJobBoardAdapter(HttpFetcher fetcher, ObjectProvider<Clock> clock) {
        return new GreenhouseJobBoardAdapter(fetcher, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    LeverPostingsAdapter leverPostingsAdapter(HttpFetcher fetcher, ObjectProvider<Clock> clock) {
        return new LeverPostingsAdapter(fetcher, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    AshbyJobBoardAdapter ashbyJobBoardAdapter(HttpFetcher fetcher, ObjectProvider<Clock> clock) {
        return new AshbyJobBoardAdapter(fetcher, clock.getIfAvailable(Clock::systemUTC));
    }
}
