package com.jobsapply.spi.common;

/** Outcome of a single hard eligibility rule. {@link #UNKNOWN} is never treated as a pass or a fail. */
public enum RuleVerdict {
    PASS,
    FAIL,
    UNKNOWN
}
