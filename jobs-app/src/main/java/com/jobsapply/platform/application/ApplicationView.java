package com.jobsapply.platform.application;

import com.jobsapply.platform.shared.model.ActorType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationView(
        UUID id,
        UUID postingId,
        UUID jobListingId,
        UUID candidateProfileId,
        UUID candidateProfileVersionId,
        UUID roleProfileVersionId,
        UUID jobMatchId,
        UUID currentPackageId,
        ApplicationStatus status,
        Instant statusChangedAt,
        List<HistoryEntry> history) {

    public record HistoryEntry(ApplicationStatus from, ApplicationStatus to, ActorType actor, String reason, Instant at) {}
}
