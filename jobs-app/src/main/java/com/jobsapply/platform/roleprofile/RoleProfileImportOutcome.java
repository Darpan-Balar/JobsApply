package com.jobsapply.platform.roleprofile;

public enum RoleProfileImportOutcome {
    /** A new profile with version 1 was created. */
    CREATED,
    /** Content changed: a new immutable version was created and activated. */
    NEW_VERSION,
    /** Content equals an older version, which was re-activated (no new version). */
    REACTIVATED,
    /** Content equals the active version. */
    UNCHANGED
}
