package com.jobsapply.spi.llm;

public record LlmResponse(String text, String model, int inputTokens, int outputTokens) {}
