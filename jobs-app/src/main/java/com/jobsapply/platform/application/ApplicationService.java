package com.jobsapply.platform.application;

import com.jobsapply.platform.application.internal.Application;
import com.jobsapply.platform.application.internal.ApplicationAttempt;
import com.jobsapply.platform.application.internal.ApplicationAttemptRepository;
import com.jobsapply.platform.application.internal.ApplicationRepository;
import com.jobsapply.platform.application.internal.SubmissionApproval;
import com.jobsapply.platform.application.internal.SubmissionApprovalRepository;
import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.model.ActorType;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the application aggregate (the current user's applications): state changes, single-use approvals and
 * submission attempts (ADR-0006). Every operation runs in one transaction and publishes its domain events inside it.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final EnumSet<ApplicationStatus> CLOSED = EnumSet.of(
            ApplicationStatus.REJECTED_BY_USER, ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED);

    private final ApplicationRepository applications;
    private final SubmissionApprovalRepository approvals;
    private final ApplicationAttemptRepository attempts;
    private final ApplicationEventPublisher events;
    private final PersonalDataCipher cipher;
    private final JsonCodec json;
    private final Clock clock;

    public record ApprovalView(UUID id, UUID packageId, String packageHash, Instant approvedAt, Instant consumedAt, Instant revokedAt,
            String revokeReason) {}

    public record AttemptView(UUID id, UUID approvalId, UUID packageId, SubmissionChannel channel, AttemptOutcome outcome,
            Instant startedAt, Instant finishedAt, SubmissionEvidence evidence, Instant verifiedAt) {}

    /** The open (not closed) application for this posting and candidate profile, or a new DISCOVERED one. */
    @Transactional
    public ApplicationView openOrCreate(UUID postingId, UUID candidateProfileId, UUID candidateVersionId, UUID roleVersionId) {
        UserContext.requireUserId();
        Optional<Application> open = applications.findOpen(postingId, candidateProfileId, CLOSED);
        if (open.isPresent()) {
            return view(open.get());
        }
        Application created = applications.save(Application.discover(postingId, candidateProfileId, candidateVersionId,
                roleVersionId, Actor.system(), Instant.now(clock)));
        publishStatus(created, null, ApplicationStatus.DISCOVERED, Actor.system(), "discovered");
        return view(created);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationView> findOpen(UUID postingId, UUID candidateProfileId) {
        UserContext.requireUserId();
        return applications.findOpen(postingId, candidateProfileId, CLOSED).map(ApplicationService::view);
    }

    /**
     * Generic transition. Leaving APPROVED other than by starting a submission revokes the approval. Approval and
     * submission states have dedicated operations below.
     */
    @Transactional
    public ApplicationView transition(UUID id, ApplicationStatus target, Actor actor, String reason) {
        if (target == ApplicationStatus.APPROVED || target == ApplicationStatus.SUBMITTING || target == ApplicationStatus.SUBMITTED
                || target == ApplicationStatus.SUBMISSION_UNCONFIRMED) {
            throw new InvalidStateTransitionException(target + " is only reachable through the approval/submission operations");
        }
        Application application = load(id);
        if (application.getStatus() != target) {
            move(application, target, actor, reason);
        }
        flush("transition");
        return view(application);
    }

    @Transactional
    public void recordPreparation(UUID id, UUID candidateVersionId, UUID roleVersionId, UUID matchId, UUID listingId, UUID packageId) {
        Application application = load(id);
        application.useInputs(candidateVersionId, roleVersionId, matchId);
        application.useDestination(listingId);
        application.attachPackage(packageId);
    }

    // --- approval ----------------------------------------------------------------------------------------------------

    /**
     * The candidate approves exactly {@code packageId} (which must be current) for one submission. The database
     * rejects a second active approval and a second live application for the same vacancy.
     */
    @Transactional
    public ApprovalView approve(UUID id, UUID packageId, String packageHash) {
        UUID user = UserContext.requireUserId();
        Application application = load(id);
        if (!packageId.equals(application.getCurrentPackageId())) {
            throw new DomainRuleViolationException("That package is not the current one; review the latest version");
        }
        if (application.getStatus() != ApplicationStatus.READY_FOR_REVIEW) {
            throw new InvalidStateTransitionException("Only an application READY_FOR_REVIEW can be approved (it is " + application.getStatus() + ")");
        }
        Instant now = Instant.now(clock);
        move(application, ApplicationStatus.APPROVED, Actor.user(user), "approved by you");
        SubmissionApproval approval = approvals.save(SubmissionApproval.builder()
                .applicationId(id).packageId(packageId).packageHash(packageHash).approvedBy(user).approvedAt(now).build());
        flush("approve");
        events.publishEvent(new ApplicationEvents.ApprovalGranted(id, user, approval.getId(), packageId, now));
        return approvalView(approval);
    }

    @Transactional
    public ApplicationView revokeApproval(UUID id) {
        UUID user = UserContext.requireUserId();
        Application application = load(id);
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new InvalidStateTransitionException("There is no approval to revoke (status " + application.getStatus() + ")");
        }
        move(application, ApplicationStatus.PREPARED, Actor.user(user), "approval revoked by you");
        flush("revoke");
        return view(application);
    }

    // --- submission ----------------------------------------------------------------------------------------------------

    /**
     * Starts a submission by consuming the active approval (single use). Replaying the same idempotency key returns the
     * same attempt; anything else without a fresh approval is refused.
     */
    @Transactional
    public AttemptView startSubmission(UUID id, String idempotencyKey, SubmissionChannel channel) {
        UUID user = UserContext.requireUserId();
        Optional<ApplicationAttempt> replay = attempts.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().getApplicationId().equals(id)) {
                throw new DomainRuleViolationException("This idempotency key was already used for another application");
            }
            return attemptView(replay.get());
        }
        Application application = load(id);
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new InvalidStateTransitionException("A submission needs a fresh approval (status " + application.getStatus() + ")");
        }
        SubmissionApproval approval = approvals.findActive(id)
                .orElseThrow(() -> new DomainRuleViolationException("No usable approval: approve the current package first"));
        if (!approval.getPackageId().equals(application.getCurrentPackageId())) {
            throw new DomainRuleViolationException("The approval was for another package version; approve the current one");
        }
        Instant now = Instant.now(clock);
        approval.consume(now);
        move(application, ApplicationStatus.SUBMITTING, Actor.user(user), "submission started");
        ApplicationAttempt attempt = attempts.save(ApplicationAttempt.builder()
                .applicationId(id).approvalId(approval.getId()).packageId(approval.getPackageId()).idempotencyKey(idempotencyKey)
                .channel(channel).outcome(AttemptOutcome.IN_PROGRESS).startedAt(now).build());
        flush("start");
        events.publishEvent(new ApplicationEvents.AttemptStarted(id, user, attempt.getId(), approval.getId(), channel, now));
        return attemptView(attempt);
    }

    /**
     * Records how the in-progress attempt ended. CONFIRMED needs credible evidence; FAILED needs a description of the
     * failure; when unsure, UNCONFIRMED — which blocks any retry until the candidate verifies it.
     */
    @Transactional
    public AttemptView recordOutcome(UUID id, UUID attemptId, AttemptOutcome outcome, SubmissionEvidence evidence, Actor actor) {
        Application application = load(id);
        ApplicationAttempt attempt = attempts.findById(attemptId).filter(a -> a.getApplicationId().equals(id))
                .orElseThrow(() -> new NotFoundException("Attempt", attemptId));
        if (application.getStatus() != ApplicationStatus.SUBMITTING || attempt.getOutcome() != AttemptOutcome.IN_PROGRESS) {
            throw new InvalidStateTransitionException("No submission is in progress for this application");
        }
        SubmissionEvidence given = orNone(evidence);
        ApplicationStatus target = switch (outcome) {
            case CONFIRMED, FAILED -> statusFor(outcome, given);
            case UNCONFIRMED -> ApplicationStatus.SUBMISSION_UNCONFIRMED;
            case IN_PROGRESS -> throw new DomainRuleViolationException("IN_PROGRESS is not an outcome");
        };
        Instant now = Instant.now(clock);
        attempt.finish(outcome, encryptEvidence(attempt, given), now);
        move(application, target, actor, "submission outcome: " + outcome);
        flush("outcome");
        events.publishEvent(new ApplicationEvents.AttemptFinished(id, application.getOwnerUserId(), attemptId, outcome, given.kind(),
                actor.type(), now));
        return attemptView(attempt);
    }

    /** The candidate resolves an unconfirmed submission (the system never can). */
    @Transactional
    public AttemptView verify(UUID id, AttemptOutcome result, SubmissionEvidence evidence) {
        UUID user = UserContext.requireUserId();
        if (result != AttemptOutcome.CONFIRMED && result != AttemptOutcome.FAILED) {
            throw new DomainRuleViolationException("Verify as CONFIRMED or FAILED");
        }
        Application application = load(id);
        if (application.getStatus() != ApplicationStatus.SUBMISSION_UNCONFIRMED) {
            throw new InvalidStateTransitionException("Only an unconfirmed submission can be verified (status " + application.getStatus() + ")");
        }
        ApplicationAttempt attempt = attempts.findFirstByApplicationIdAndOutcome(id, AttemptOutcome.UNCONFIRMED)
                .orElseThrow(() -> new NotFoundException("Unconfirmed attempt", id));
        SubmissionEvidence given = orNone(evidence);
        ApplicationStatus target = statusFor(result, given);
        Instant now = Instant.now(clock);
        attempt.verify(result, user, encryptEvidence(attempt, given), now);
        move(application, target, Actor.user(user), "verified by you");
        flush("verify");
        events.publishEvent(new ApplicationEvents.AttemptVerified(id, user, attempt.getId(), result, now));
        return attemptView(attempt);
    }

    /** Crash recovery: an attempt interrupted mid-submission becomes unconfirmed — never retried automatically. */
    @Transactional
    public void markInterrupted(UUID id) {
        Application application = load(id);
        if (application.getStatus() != ApplicationStatus.SUBMITTING) {
            return;
        }
        Instant now = Instant.now(clock);
        Optional<ApplicationAttempt> attempt = attempts.findFirstByApplicationIdAndOutcome(id, AttemptOutcome.IN_PROGRESS);
        attempt.ifPresent(a -> a.finish(AttemptOutcome.UNCONFIRMED, null, now));
        move(application, ApplicationStatus.SUBMISSION_UNCONFIRMED, Actor.system(), "the application stopped during the submission");
        flush("recover");
        attempt.ifPresent(a -> events.publishEvent(new ApplicationEvents.AttemptFinished(id, application.getOwnerUserId(), a.getId(),
                AttemptOutcome.UNCONFIRMED, SubmissionEvidence.Kind.NONE, ActorType.SYSTEM, now)));
    }

    @Transactional
    public ApplicationView withdraw(UUID id) {
        UUID user = UserContext.requireUserId();
        Application application = load(id);
        move(application, ApplicationStatus.WITHDRAWN, Actor.user(user), "withdrawn by you");
        flush("withdraw");
        return view(application);
    }

    // --- queries -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ApplicationView get(UUID id) {
        return view(load(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list() {
        UserContext.requireUserId();
        return applications.findAllByOrderByStatusChangedAtDesc().stream().map(ApplicationService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalView> approvals(UUID id) {
        load(id);
        return approvals.findByApplicationIdOrderByApprovedAtDesc(id).stream().map(ApplicationService::approvalView).toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptView> attempts(UUID id) {
        load(id);
        return attempts.findByApplicationIdOrderByStartedAtDesc(id).stream().map(this::attemptView).toList();
    }

    // --- internals ---------------------------------------------------------------------------------------------------

    private void move(Application application, ApplicationStatus target, Actor actor, String reason) {
        ApplicationStatus from = application.getStatus();
        Instant now = Instant.now(clock);
        application.transitionTo(target, actor, reason, now);
        if (from == ApplicationStatus.APPROVED && target != ApplicationStatus.SUBMITTING) {
            approvals.findActive(application.getId()).ifPresent(a -> {
                a.revoke(now, "left APPROVED: " + target);
                events.publishEvent(new ApplicationEvents.ApprovalRevoked(application.getId(), application.getOwnerUserId(), a.getId(),
                        "LEFT_APPROVED_" + target, now));
            });
        }
        publishStatus(application, from, target, actor, reason);
    }

    private void publishStatus(Application application, ApplicationStatus from, ApplicationStatus to, Actor actor, String reason) {
        UUID owner = application.getOwnerUserId() != null ? application.getOwnerUserId() : UserContext.requireUserId();
        events.publishEvent(new ApplicationEvents.StatusChanged(application.getId(), owner, from, to, actor.type(), actor.userId(),
                actor.isUser() ? null : reason, Instant.now(clock)));
    }

    /** Turns constraint violations into domain errors with clear messages (the transaction rolls back). */
    private void flush(String operation) {
        try {
            applications.flush();
        } catch (DataIntegrityViolationException e) {
            String constraint = String.valueOf(e.getMostSpecificCause().getMessage());
            if (constraint.contains("uk_application_live_per_posting")) {
                throw new DomainRuleViolationException("You already have an approved, in-progress or submitted application for this vacancy");
            }
            if (constraint.contains("uk_submission_approval_active") || constraint.contains("uk_application_attempt")) {
                throw new ConcurrencyConflictException("Another " + operation + " of this application happened at the same time; reload and retry");
            }
            throw e;
        }
    }

    /** The same evidence rules for reported and verified outcomes: CONFIRMED needs credible evidence, FAILED a description. */
    private static ApplicationStatus statusFor(AttemptOutcome outcome, SubmissionEvidence evidence) {
        if (outcome == AttemptOutcome.CONFIRMED) {
            if (!evidence.credible()) {
                throw new DomainRuleViolationException("A confirmed submission needs evidence (confirmation page, e-mail, "
                        + "screenshot or employer portal) with a description; report UNCONFIRMED if you are not sure");
            }
            return ApplicationStatus.SUBMITTED;
        }
        if (evidence.detail() == null || evidence.detail().isBlank()) {
            throw new DomainRuleViolationException("Describe what failed, so the failure is definite");
        }
        return ApplicationStatus.FAILED;
    }

    private static SubmissionEvidence orNone(SubmissionEvidence evidence) {
        return evidence == null ? new SubmissionEvidence(SubmissionEvidence.Kind.NONE, null, null) : evidence;
    }

    private String encryptEvidence(ApplicationAttempt attempt, SubmissionEvidence evidence) {
        return cipher.encrypt(json.write(evidence), "application_attempt|" + attempt.getId() + "|" + attempt.getOwnerUserIdOrCurrent());
    }

    private Application load(UUID id) {
        UserContext.requireUserId();
        return applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
    }

    private static ApprovalView approvalView(SubmissionApproval a) {
        return new ApprovalView(a.getId(), a.getPackageId(), a.getPackageHash(), a.getApprovedAt(), a.getConsumedAt(), a.getRevokedAt(),
                a.getRevokeReason());
    }

    private AttemptView attemptView(ApplicationAttempt a) {
        SubmissionEvidence evidence = a.getEvidenceJson() == null ? null : json.read(
                cipher.decrypt(a.getEvidenceJson(), "application_attempt|" + a.getId() + "|" + a.getOwnerUserIdOrCurrent()), SubmissionEvidence.class);
        return new AttemptView(a.getId(), a.getApprovalId(), a.getPackageId(), a.getChannel(), a.getOutcome(), a.getStartedAt(),
                a.getFinishedAt(), evidence, a.getVerifiedAt());
    }

    private static ApplicationView view(Application a) {
        return new ApplicationView(a.getId(), a.getJobPostingId(), a.getJobListingId(), a.getCandidateProfileId(),
                a.getCandidateProfileVersionId(), a.getRoleProfileVersionId(), a.getJobMatchId(), a.getCurrentPackageId(),
                a.getStatus(), a.getStatusChangedAt(),
                a.getHistory().stream().map(h -> new ApplicationView.HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getActorType(),
                        h.getReason(), h.getOccurredAt())).toList());
    }
}
