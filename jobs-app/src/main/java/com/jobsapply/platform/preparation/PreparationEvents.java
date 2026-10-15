package com.jobsapply.platform.preparation;

import java.time.Instant;
import java.util.UUID;

/** Domain events of application preparation (ids and counts only). */
public final class PreparationEvents {

    private PreparationEvents() {}

    /** @param actorUserId the user whose request created the package, or {@code null} for the system */
    public record PackageCreated(UUID applicationId, UUID ownerId, UUID actorUserId, UUID packageId, int versionNo, int blockingIssues,
            int warnings, Instant at) {}
}
