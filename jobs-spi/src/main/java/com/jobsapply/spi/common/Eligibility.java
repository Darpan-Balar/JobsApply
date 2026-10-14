package com.jobsapply.spi.common;

import java.util.Collection;

/** Aggregate eligibility of a candidate for a job, derived from hard-rule verdicts only. */
public enum Eligibility {
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNKNOWN;

    /**
     * Combines rule verdicts: any {@code FAIL} ⇒ {@code NOT_ELIGIBLE}; otherwise any {@code UNKNOWN} ⇒ {@code UNKNOWN};
     * otherwise {@code ELIGIBLE}. With no rules configured the result is {@code ELIGIBLE}.
     */
    public static Eligibility fromVerdicts(Collection<RuleVerdict> verdicts) {
        if (verdicts.contains(RuleVerdict.FAIL)) {
            return NOT_ELIGIBLE;
        }
        if (verdicts.contains(RuleVerdict.UNKNOWN)) {
            return UNKNOWN;
        }
        return ELIGIBLE;
    }
}
