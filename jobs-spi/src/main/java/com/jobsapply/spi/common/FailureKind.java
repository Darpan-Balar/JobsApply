package com.jobsapply.spi.common;

/** Classification of an expected plugin failure. Drives retry and availability decisions in the core. */
public enum FailureKind {
    /** Network error, timeout or 5xx: retry with backoff may succeed. Never proof that a job closed. */
    TRANSIENT,
    /** Throttled by the provider; honour {@code retryAfter}. */
    RATE_LIMITED,
    /** Credentials missing, invalid or insufficient. Do not retry. */
    AUTH,
    /** The addressed resource does not exist at the provider. */
    NOT_FOUND,
    /** The provider answered but the payload could not be understood. Do not retry blindly. */
    PARSE,
    /** Any other non-retryable failure. */
    PERMANENT
}
