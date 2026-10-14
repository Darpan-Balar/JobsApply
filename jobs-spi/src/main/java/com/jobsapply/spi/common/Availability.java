package com.jobsapply.spi.common;

/**
 * Whether a vacancy is open. {@link #UNKNOWN} is the answer whenever evidence is not authoritative — in particular a
 * failed request never yields {@link #CLOSED}.
 */
public enum Availability {
    OPEN,
    CLOSED,
    UNKNOWN
}
