package com.jobsapply.platform.discovery;

public enum SyncOutcome {
    SUCCEEDED,
    /** Some pages, items or availability checks failed; everything that succeeded was applied. */
    PARTIAL,
    /** Nothing usable was fetched; no listing was changed. */
    FAILED,
    /** Another run for the same binding is in progress (database single-flight). */
    SKIPPED_ALREADY_RUNNING
}
