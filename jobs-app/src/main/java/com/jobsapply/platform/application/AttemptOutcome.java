package com.jobsapply.platform.application;

public enum AttemptOutcome {
    IN_PROGRESS,
    /** Credible confirmation evidence was recorded. */
    CONFIRMED,
    /** A definite failure (e.g. the form rejected the submission). */
    FAILED,
    /** The outcome is not known; must be verified by the candidate before anything else happens. */
    UNCONFIRMED
}
