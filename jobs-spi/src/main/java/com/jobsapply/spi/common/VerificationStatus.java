package com.jobsapply.spi.common;

/** Provenance of a candidate fact. Only {@link #VERIFIED} facts may back generated claims without further approval. */
public enum VerificationStatus {
    /** Confirmed by the candidate. */
    VERIFIED,
    /** A preference the candidate stated (not a qualification). */
    USER_PREFERENCE,
    /** Computed from other facts (e.g. total years of experience). */
    DERIVED,
    /** Imported or inferred but not yet confirmed. Must not back any claim. */
    UNVERIFIED,
    /** Replaced by a newer fact; kept for history. Must not back any claim. */
    SUPERSEDED;

    public boolean canBackClaims() {
        return this == VERIFIED || this == USER_PREFERENCE || this == DERIVED;
    }
}
