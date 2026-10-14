package com.jobsapply.platform.preparation;

import java.time.Instant;
import java.util.UUID;

/** Domain events of application preparation (ids and counts only). */
public final class PreparationEvents {

    private PreparationEvents() {}

    public record PackageCreated(UUID applicationId, UUID ownerId, UUID packageId, int versionNo, int blockingIssues, int warnings,
            Instant at) {}
}
