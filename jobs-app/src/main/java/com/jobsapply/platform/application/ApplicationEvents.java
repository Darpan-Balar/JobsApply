package com.jobsapply.platform.application;

import com.jobsapply.platform.shared.model.ActorType;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the application aggregate. They are stored in the event publication registry in the changing
 * transaction and delivered to listeners after commit (ADR-0014), so they must be self-contained (including who acted)
 * and serializable. They carry ids, statuses and codes only — never personal data.
 */
public final class ApplicationEvents {

    private ApplicationEvents() {}

    /** @param reason only system-generated reasons; free text typed by the user is never carried */
    public record StatusChanged(UUID applicationId, UUID ownerId, ApplicationStatus from, ApplicationStatus to,
            ActorType actorType, UUID actorUserId, String reason, Instant at) {}

    public record ApprovalGranted(UUID applicationId, UUID ownerId, UUID approvalId, UUID packageId, Instant at) {}

    /** @param actorUserId who caused the revocation, or {@code null} for the system */
    public record ApprovalRevoked(UUID applicationId, UUID ownerId, UUID approvalId, String reasonCode, ActorType actorType, UUID actorUserId,
            Instant at) {}

    public record AttemptStarted(UUID applicationId, UUID ownerId, UUID attemptId, UUID approvalId, SubmissionChannel channel, Instant at) {}

    public record AttemptFinished(UUID applicationId, UUID ownerId, UUID attemptId, AttemptOutcome outcome,
            SubmissionEvidence.Kind evidenceKind, ActorType actorType, Instant at) {}

    public record AttemptVerified(UUID applicationId, UUID ownerId, UUID attemptId, AttemptOutcome outcome, Instant at) {}
}
