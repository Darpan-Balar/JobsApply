package com.jobsapply.spi.source;

/**
 * A location exactly as published, plus any structured parts the provider supplied. Unknown parts are {@code null};
 * the core normalizes without discarding {@code text}.
 */
public record RawLocation(String text, String city, String region, String countryCode, Boolean remote) {}
