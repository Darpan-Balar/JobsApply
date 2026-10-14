package com.jobsapply.adapters;

import com.jobsapply.adapters.http.HostRateLimiter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.adapters.http.JdkHttpTransport;
import com.jobsapply.adapters.http.Sleeper;
import com.jobsapply.adapters.source.ashby.AshbyJobBoardAdapter;
import com.jobsapply.adapters.source.greenhouse.GreenhouseJobBoardAdapter;
import com.jobsapply.adapters.source.lever.LeverPostingsAdapter;
import com.jobsapply.adapters.matching.EligibilityRules;
import com.jobsapply.adapters.preparation.FactBasedCoverNoteGenerator;
import com.jobsapply.adapters.preparation.FactBasedResumeGenerator;
import com.jobsapply.adapters.preparation.RuleBasedAnswerDrafter;
import com.jobsapply.adapters.render.HtmlResumeRenderer;
import com.jobsapply.adapters.render.PdfResumeRenderer;
import com.jobsapply.adapters.matching.ScoreComponents;
import com.jobsapply.adapters.matching.WeightedMatchingStrategy;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.ScoreComponent;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
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

    /** Built-in eligibility rules, score components and the weighted strategy, as individual plugins. */
    @Bean
    static BeanDefinitionRegistryPostProcessor builtInMatchingPlugins() {
        return registry -> {
            for (EligibilityRule rule : EligibilityRules.all()) {
                registry.registerBeanDefinition("eligibilityRule." + rule.ruleType(),
                        BeanDefinitionBuilder.genericBeanDefinition(EligibilityRule.class, () -> rule).getBeanDefinition());
            }
            for (ScoreComponent component : ScoreComponents.all()) {
                registry.registerBeanDefinition("scoreComponent." + component.componentKey(),
                        BeanDefinitionBuilder.genericBeanDefinition(ScoreComponent.class, () -> component).getBeanDefinition());
            }
        };
    }

    @Bean
    FactBasedResumeGenerator factBasedResumeGenerator() {
        return new FactBasedResumeGenerator();
    }

    @Bean
    FactBasedCoverNoteGenerator factBasedCoverNoteGenerator() {
        return new FactBasedCoverNoteGenerator();
    }

    @Bean
    RuleBasedAnswerDrafter ruleBasedAnswerDrafter() {
        return new RuleBasedAnswerDrafter();
    }

    @Bean
    HtmlResumeRenderer htmlResumeRenderer() {
        return new HtmlResumeRenderer();
    }

    @Bean
    PdfResumeRenderer pdfResumeRenderer() {
        return new PdfResumeRenderer();
    }

    @Bean
    WeightedMatchingStrategy weightedMatchingStrategy(List<EligibilityRule> rules, List<ScoreComponent> components) {
        return new WeightedMatchingStrategy(rules, components);
    }
}
