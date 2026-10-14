package com.jobsapply.platform.candidate;

import java.time.Instant;
import java.util.UUID;

/** Domain events of candidate profiles (ids and system notes only — never profile content). */
public final class CandidateEvents {

    private CandidateEvents() {}

    public record VersionCreated(UUID profileId, UUID versionId, int versionNo, UUID ownerId, String note, Instant at) {}
}
