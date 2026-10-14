package com.jobsapply.spi.common;

/**
 * Optional operations a plugin may support. Callers must check {@link PluginDescriptor#supports(Capability)} before
 * invoking an operation; a plugin asked for an undeclared capability returns {@link PluginResult.Unsupported}.
 */
public enum Capability {
    // Job sources
    LIST_JOBS,
    JOB_DETAILS,
    AVAILABILITY_CHECK,
    INCREMENTAL_SYNC,
    COMPENSATION,
    APPLICATION_QUESTIONS,
    /** Source documents an authorized application-submission API. Listing access alone never implies this. */
    APPLY_VIA_API,
    // Application adapters
    OPEN_DESTINATION,
    INSPECT_FIELDS,
    FILL_FIELDS,
    VALIDATE_FORM,
    DETECT_CONFIRMATION
}
