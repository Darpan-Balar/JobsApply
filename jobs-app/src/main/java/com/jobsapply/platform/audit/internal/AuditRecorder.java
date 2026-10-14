package com.jobsapply.platform.audit.internal;

import com.jobsapply.platform.application.ApplicationEvents;
import com.jobsapply.platform.candidate.CandidateEvents;
import com.jobsapply.platform.preparation.PreparationEvents;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.ActorType;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Writes domain events to the audit log in the publishing transaction. */
@Component
@RequiredArgsConstructor
class AuditRecorder {

    private final AuditEventRepository audit;
    private final JsonCodec json;

    @EventListener
    void on(ApplicationEvents.StatusChanged e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", e.from() == null ? null : e.from().name());
        payload.put("to", e.to().name());
        if (e.reason() != null) {
            payload.put("reason", e.reason());
        }
        record("application", e.applicationId(), "status-changed", e.actorType(), e.actorUserId(), e.ownerId(), e.at(), payload);
    }

    @EventListener
    void on(ApplicationEvents.ApprovalGranted e) {
        record("application", e.applicationId(), "approval-granted", ActorType.USER, e.ownerId(), e.ownerId(), e.at(),
                Map.of("approvalId", e.approvalId(), "packageId", e.packageId()));
    }

    @EventListener
    void on(ApplicationEvents.ApprovalRevoked e) {
        record("application", e.applicationId(), "approval-revoked", actorType(), UserContext.currentUserId().orElse(null), e.ownerId(),
                e.at(), Map.of("approvalId", e.approvalId(), "reason", e.reasonCode()));
    }

    @EventListener
    void on(ApplicationEvents.AttemptStarted e) {
        record("application", e.applicationId(), "submission-started", ActorType.USER, e.ownerId(), e.ownerId(), e.at(),
                Map.of("attemptId", e.attemptId(), "approvalId", e.approvalId(), "channel", e.channel().name()));
    }

    @EventListener
    void on(ApplicationEvents.AttemptFinished e) {
        record("application", e.applicationId(), "submission-finished", e.actorType(),
                e.actorType() == ActorType.USER ? e.ownerId() : null, e.ownerId(), e.at(),
                Map.of("attemptId", e.attemptId(), "outcome", e.outcome().name(), "evidenceKind", e.evidenceKind().name()));
    }

    @EventListener
    void on(ApplicationEvents.AttemptVerified e) {
        record("application", e.applicationId(), "submission-verified", ActorType.USER, e.ownerId(), e.ownerId(), e.at(),
                Map.of("attemptId", e.attemptId(), "outcome", e.outcome().name()));
    }

    @EventListener
    void on(CandidateEvents.VersionCreated e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("versionId", e.versionId());
        payload.put("versionNo", e.versionNo());
        if (e.note() != null) {
            payload.put("note", e.note());
        }
        record("candidate-profile", e.profileId(), "version-created", ActorType.USER, e.ownerId(), e.ownerId(), e.at(), payload);
    }

    @EventListener
    void on(PreparationEvents.PackageCreated e) {
        record("application", e.applicationId(), "package-created", actorType(), UserContext.currentUserId().orElse(null), e.ownerId(),
                e.at(), Map.of("packageId", e.packageId(), "versionNo", e.versionNo(), "blockingIssues", e.blockingIssues(),
                        "warnings", e.warnings()));
    }

    private static ActorType actorType() {
        return UserContext.currentUserId().isPresent() ? ActorType.USER : ActorType.SYSTEM;
    }

    private void record(String aggregateType, UUID aggregateId, String eventType, ActorType actorType, UUID actorUserId, UUID subject,
            Instant at, Map<String, Object> payload) {
        audit.save(AuditEvent.builder()
                .occurredAt(at)
                .actorType(actorType)
                .actorUserId(actorType == ActorType.USER ? actorUserId : null)
                .subjectUserId(subject)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payloadJson(json.write(payload))
                .build());
    }
}
