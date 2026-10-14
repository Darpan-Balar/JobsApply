package com.jobsapply.platform.posting;

public enum IngestOutcome {
    INSERTED,
    /** Content changed, or a closed listing was seen again (reopened). */
    UPDATED,
    /** Content identical; only "last seen" was refreshed. */
    UNCHANGED
}
