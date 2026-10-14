package com.jobsapply.platform.submission;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.application.ApplicationService.ApprovalView;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionChannel;
import com.jobsapply.platform.application.SubmissionEvidence;
import com.jobsapply.platform.preparation.PackageContent;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationViews;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.common.Availability;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The candidate's approval and submission actions. Approval is refused unless the package the candidate saw is current
 * and complete: no blocking issues, every required question answered, availability confirmed. Each approval allows
 * exactly one submission attempt.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

    private final ApplicationService applications;
    private final PreparationService preparation;

    public record StartedSubmission(AttemptView attempt, String applicationUrl, String instructions) {}

    /** Why an application cannot be approved yet (empty = approvable). */
    public List<String> approvalBlockers(UUID applicationId) {
        PreparationViews.ApplicationDetail detail = preparation.detail(applicationId);
        return blockers(detail);
    }

    public ApprovalView approve(UUID applicationId, UUID packageId) {
        UserContext.requireUserId();
        PreparationViews.ApplicationDetail detail = preparation.detail(applicationId);
        if (detail.currentPackage() == null || !detail.currentPackage().id().equals(packageId)) {
            throw new DomainRuleViolationException("That package is not the current one; review the latest version before approving");
        }
        List<String> blockers = blockers(detail);
        if (!blockers.isEmpty()) {
            throw new DomainRuleViolationException("Cannot approve yet: " + String.join("; ", blockers));
        }
        return applications.approve(applicationId, packageId, detail.currentPackage().contentHash());
    }

    /** Revokes the approval; the application returns to review. */
    public ApplicationView revoke(UUID applicationId) {
        applications.revokeApproval(applicationId);
        PreparationViews.ApplicationDetail detail = preparation.detail(applicationId);
        if (detail.currentPackage() != null && detail.currentPackage().blockingIssues() == 0) {
            return applications.transition(applicationId, ApplicationStatus.READY_FOR_REVIEW,
                    Actor.system(), "back to review after revocation");
        }
        return detail.application();
    }

    /**
     * Consumes the approval and marks the application SUBMITTING. The candidate then submits at {@code applicationUrl} in
     * their own browser and reports the outcome. Replaying the same key returns the same attempt.
     */
    public StartedSubmission start(UUID applicationId, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new DomainRuleViolationException("An Idempotency-Key of 8-100 characters [A-Za-z0-9._:-] is required");
        }
        AttemptView attempt = applications.startSubmission(applicationId, idempotencyKey, SubmissionChannel.MANUAL);
        PackageContent content = preparation.detail(applicationId).currentPackage().content();
        return new StartedSubmission(attempt, content.destination() == null ? null : content.destination().applicationUrl(),
                "Open the application link, review and submit the form yourself, then report the outcome. Report CONFIRMED only with "
                        + "evidence (confirmation page or e-mail); if you are not sure, report UNCONFIRMED.");
    }

    public AttemptView reportOutcome(UUID applicationId, UUID attemptId, AttemptOutcome outcome, SubmissionEvidence evidence) {
        return applications.recordOutcome(applicationId, attemptId, outcome, evidence, Actor.user(UserContext.requireUserId()));
    }

    public AttemptView verify(UUID applicationId, AttemptOutcome result, SubmissionEvidence evidence) {
        return applications.verify(applicationId, result, evidence);
    }

    public ApplicationView withdraw(UUID applicationId) {
        return applications.withdraw(applicationId);
    }

    public List<ApprovalView> approvals(UUID applicationId) {
        return applications.approvals(applicationId);
    }

    public List<AttemptView> attempts(UUID applicationId) {
        return applications.attempts(applicationId);
    }

    private static List<String> blockers(PreparationViews.ApplicationDetail detail) {
        List<String> blockers = new ArrayList<>();
        if (detail.currentPackage() == null) {
            blockers.add("no package has been prepared");
            return blockers;
        }
        PackageContent content = detail.currentPackage().content();
        content.issues().stream().filter(i -> i.severity() == PackageContent.PackageIssue.Severity.BLOCKING)
                .forEach(i -> blockers.add(i.code() + ": " + i.message()));
        content.unanswered().stream().filter(a -> a.question().required())
                .forEach(a -> blockers.add("required question unanswered: " + a.question().text()));
        if (content.availability().availability() != Availability.OPEN) {
            blockers.add("availability was not confirmed open when the package was prepared");
        }
        return blockers;
    }
}
