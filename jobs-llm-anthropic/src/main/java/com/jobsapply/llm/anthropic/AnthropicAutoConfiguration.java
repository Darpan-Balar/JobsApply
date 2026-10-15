package com.jobsapply.llm.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Registers the Claude extractor only when an API key is configured (ADR-0015). */
@AutoConfiguration
@ConditionalOnExpression("!'${jobs.llm.anthropic.api-key:}'.isBlank()")
public class AnthropicAutoConfiguration {

    @Bean
    AnthropicSettings anthropicSettings(Environment env) {
        return new AnthropicSettings(env.getProperty("jobs.llm.anthropic.api-key"), env.getProperty("jobs.llm.anthropic.model"),
                env.getProperty("jobs.llm.anthropic.effort"), env.getProperty("jobs.llm.anthropic.timeout", Duration.class),
                env.getProperty("jobs.llm.anthropic.base-url"));
    }

    @Bean(destroyMethod = "close")
    AnthropicClient anthropicClient(AnthropicSettings settings) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(settings.apiKey()).timeout(settings.timeout()).maxRetries(2);
        if (settings.baseUrl() != null && !settings.baseUrl().isBlank()) {
            builder.baseUrl(settings.baseUrl());
        }
        return builder.build();
    }

    @Bean
    ClaudeResumeExtractor claudeResumeExtractor(AnthropicClient client, AnthropicSettings settings) {
        return new ClaudeResumeExtractor(client, settings);
    }
}
