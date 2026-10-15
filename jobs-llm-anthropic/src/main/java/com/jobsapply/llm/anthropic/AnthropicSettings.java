package com.jobsapply.llm.anthropic;

import java.time.Duration;
import java.util.Objects;

/**
 * {@code jobs.llm.anthropic.*}. The API key comes from the environment ({@code ANTHROPIC_API_KEY}, e.g. in the
 * git-ignored {@code .env}); without it nothing is registered and nothing is ever sent.
 *
 * @param effort {@code low} suffices for extraction; raise it only if results are poor
 * @param baseUrl only for tests (a local stand-in for the API)
 */
public record AnthropicSettings(String apiKey, String model, String effort, Duration timeout, String baseUrl) {

    public AnthropicSettings {
        model = model == null || model.isBlank() ? "claude-opus-5-5" : model.strip();
        effort = effort == null || effort.isBlank() ? "low" : effort.strip();
        timeout = Objects.requireNonNullElse(timeout, Duration.ofMinutes(2));
    }

    @Override
    public String toString() {
        return "AnthropicSettings[model=" + model + ", effort=" + effort + ", apiKey redacted]";
    }
}
