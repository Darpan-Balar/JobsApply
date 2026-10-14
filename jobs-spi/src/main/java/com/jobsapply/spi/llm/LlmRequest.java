package com.jobsapply.spi.llm;

/**
 * @param purpose short label for audit/metrics (e.g. {@code semantic-match})
 * @param redaction whether the platform has already redacted personal data from the prompts
 */
public record LlmRequest(
        String purpose, String systemPrompt, String userPrompt, int maxOutputTokens, Redaction redaction) {

    public enum Redaction {
        REDACTED,
        CONTAINS_PERSONAL_DATA
    }
}
