package com.jobsapply.platform.assist;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionChannel;
import com.jobsapply.platform.application.SubmissionEvidence;
import com.jobsapply.platform.assist.AssistView.Field;
import com.jobsapply.platform.assist.AssistView.State;
import com.jobsapply.platform.assist.internal.AssistSessions;
import com.jobsapply.platform.assist.internal.FieldMappingPlanner;
import com.jobsapply.platform.assist.internal.FieldMappingPlanner.Decision;
import com.jobsapply.platform.assist.internal.LiveSession;
import com.jobsapply.platform.assist.internal.PlatformSession;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.preparation.PackageContent;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationViews;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.storage.DocumentStore;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.application.ApplicationAdapter;
import com.jobsapply.spi.application.ApplicationDestination;
import com.jobsapply.spi.application.ConfirmationEvidence;
import com.jobsapply.spi.application.FieldMappingPlan.Attachment;
import com.jobsapply.spi.application.FillReport;
import com.jobsapply.spi.application.FormInspection;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Browser assistance for one approved application at a time (ADR-0012). Starting it uses the approval exactly like a
 * manual start (the attempt and SUBMITTING are persisted before any browser action), then opens the form, fills what is
 * certain, and leaves the rest — and the Submit button — to the candidate. Browser work never runs inside a database
 * transaction.
 */
@Service
@RequiredArgsConstructor
public class AssistService {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final String SUBMIT_YOURSELF = "Check every field in the window, complete the ones left to you, then press Submit "
            + "yourself. Afterwards ask for the confirmation to be recorded; if you are not sure it went through, report UNCONFIRMED.";

    private final ApplicationService applications;
    private final PreparationService preparation;
    private final PluginRegistry<ApplicationAdapter> adapters;
    private final AssistSessions sessions;
    private final DocumentStore store;
    private final AssistProperties properties;
    private final FieldMappingPlanner planner = new FieldMappingPlanner();

    /**
     * Uses the approval and opens the form. Replaying the same key reopens the window of an attempt still in progress
     * (e.g. after it was closed); it never starts a second attempt.
     */
    public AssistView start(UUID applicationId, String idempotencyKey) {
        UUID owner = UserContext.requireUserId();
        PackageContent content = currentPackage(applicationId);
        ApplicationDestination destination = destination(content);
        ApplicationAdapter adapter = adapter(destination);
        LiveSession live = sessions.reserve(owner, applicationId);
        live.lock(LOCK_WAIT);
        try {
            if (live.window() != null) {
                return live.last();
            }
            AttemptView attempt;
            try {
                attempt = applications.startSubmission(applicationId, idempotencyKey, SubmissionChannel.BROWSER_ASSISTED);
            } catch (RuntimeException e) {
                sessions.release(live);
                throw e;
            }
            if (attempt.channel() != SubmissionChannel.BROWSER_ASSISTED) {
                sessions.release(live);
                throw new DomainRuleViolationException("This Idempotency-Key started a manual submission; report its outcome instead");
            }
            if (attempt.outcome() != AttemptOutcome.IN_PROGRESS) {
                sessions.release(live);
                return AssistView.of(applicationId, attempt.id(), State.FINISHED, attempt.outcome(), "This attempt has already ended.");
            }
            PlatformSession session = new PlatformSession(UUID.randomUUID(), applicationId, destination, owner, store);
            live.opened(attempt.id(), session);
            return switch (adapter.open(session)) {
                case PluginResult.Ok<FormInspection> ok -> live.remember(afterInspection(live, adapter, ok.value(), content));
                case PluginResult.Failed<FormInspection> f -> openFailed(live, adapter, f.kind(), f.message());
                case PluginResult.Unsupported<FormInspection> u -> openFailed(live, adapter, FailureKind.PERMANENT, "unsupported");
            };
        } finally {
            live.unlock();
        }
    }

    /** Inspects the page again (after you signed in, solved a challenge or navigated to the form) and fills what is certain. */
    public AssistView refresh(UUID applicationId) {
        LiveSession live = live(applicationId);
        live.lock(LOCK_WAIT);
        try {
            ApplicationAdapter adapter = adapter(live.window().destination());
            return switch (adapter.inspect(live.window())) {
                case PluginResult.Ok<FormInspection> ok -> live.remember(afterInspection(live, adapter, ok.value(), currentPackage(applicationId)));
                case PluginResult.Failed<FormInspection> f -> windowGone(live, adapter);
                case PluginResult.Unsupported<FormInspection> u -> windowGone(live, adapter);
            };
        } finally {
            live.unlock();
        }
    }

    /**
     * Waits up to {@code timeout} for the confirmation page after you pressed Submit. A confirmation seen on the page is
     * recorded as evidence (message, address, screenshot) and the application becomes SUBMITTED. Nothing seen means
     * nothing is recorded: look again, or report the outcome yourself.
     */
    public AssistView confirm(UUID applicationId, Duration timeout) {
        LiveSession live = live(applicationId);
        Duration wait = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(30)
                : timeout.compareTo(properties.maxObserve()) > 0 ? properties.maxObserve() : timeout;
        live.lock(LOCK_WAIT);
        try {
            ApplicationAdapter adapter = adapter(live.window().destination());
            PluginResult<ConfirmationEvidence> observed = adapter.observeConfirmation(live.window(), wait);
            if (observed instanceof PluginResult.Ok<ConfirmationEvidence>(ConfirmationEvidence evidence)
                    && evidence.kind() != ConfirmationEvidence.Kind.NONE && evidence.detail() != null && !evidence.detail().isBlank()) {
                AttemptView attempt = applications.recordOutcome(applicationId, live.attemptId(), AttemptOutcome.CONFIRMED,
                        new SubmissionEvidence(SubmissionEvidence.Kind.CONFIRMATION_PAGE, evidence.detail(),
                                evidence.pageUrl() == null ? null : evidence.pageUrl().toString(), evidence.artifactRef()),
                        Actor.system());
                closeWindow(live, adapter);
                return AssistView.of(applicationId, attempt.id(), State.CONFIRMED, attempt.outcome(),
                        "The confirmation was recorded as evidence. Nothing else was started.");
            }
            if (adapter.validate(live.window()) instanceof PluginResult.Failed<ValidationReport>) {
                return windowGone(live, adapter);
            }
            AssistView last = live.last();
            return new AssistView(applicationId, live.attemptId(), State.READY_FOR_YOU, AttemptOutcome.IN_PROGRESS,
                    last == null ? List.of() : last.blockers(), last == null ? List.of() : last.humanSteps(),
                    last == null ? List.of() : last.filled(), last == null ? List.of() : last.leftToYou(),
                    last == null ? List.of() : last.requiredStillEmpty(),
                    "No confirmation seen yet. If you have submitted, ask again; if you cannot tell, report UNCONFIRMED.");
        } finally {
            live.unlock();
        }
    }

    /** Closes the window. The attempt stays in progress until you report how it ended. */
    public AssistView close(UUID applicationId) {
        LiveSession live = live(applicationId);
        live.lock(LOCK_WAIT);
        try {
            closeWindow(live, adapter(live.window().destination()));
            return AssistView.of(applicationId, live.attemptId(), State.WINDOW_NOT_OPEN, AttemptOutcome.IN_PROGRESS, reportOutcome());
        } finally {
            live.unlock();
        }
    }

    public AssistView view(UUID applicationId) {
        UUID owner = UserContext.requireUserId();
        applications.get(applicationId);
        Optional<LiveSession> live = sessions.find(owner, applicationId).filter(s -> s.last() != null);
        if (live.isPresent()) {
            return live.get().last();
        }
        AttemptView attempt = applications.attempts(applicationId).stream()
                .filter(a -> a.channel() == SubmissionChannel.BROWSER_ASSISTED).findFirst()
                .orElseThrow(() -> new NotFoundException("Browser assistance", applicationId));
        return attempt.outcome() == AttemptOutcome.IN_PROGRESS
                ? AssistView.of(applicationId, attempt.id(), State.WINDOW_NOT_OPEN, attempt.outcome(), reportOutcome())
                : AssistView.of(applicationId, attempt.id(), State.FINISHED, attempt.outcome(), "This attempt has ended.");
    }

    /** The confirmation screenshot recorded as evidence for this attempt (PNG). */
    public byte[] screenshot(UUID applicationId, UUID attemptId) {
        UUID owner = UserContext.requireUserId();
        String ref = applications.attempts(applicationId).stream().filter(a -> a.id().equals(attemptId)).findFirst()
                .map(AttemptView::evidence).map(SubmissionEvidence::artifactRef)
                .orElseThrow(() -> new NotFoundException("Screenshot", attemptId));
        return store.get(owner, ref);
    }

    /** Closes the window of an application whose attempt ended some other way (e.g. you reported the outcome). */
    public void closeFor(UUID applicationId) {
        sessions.findByApplication(applicationId).ifPresent(live -> {
            if (live.window() != null) {
                adapters.find(properties.adapter()).ifPresent(a -> a.close(live.window()));
            }
            sessions.release(live);
        });
    }

    // --- internals -----------------------------------------------------------------------------------------------------

    private AssistView afterInspection(LiveSession live, ApplicationAdapter adapter, FormInspection inspection, PackageContent content) {
        UUID applicationId = live.applicationId();
        if (inspection.blocked()) {
            return new AssistView(applicationId, live.attemptId(), State.PAUSED_FOR_YOU, AttemptOutcome.IN_PROGRESS, inspection.blockers(),
                    inspection.humanSteps(), List.of(), List.of(), List.of(), "Paused: " + inspection.blockers() + ". Nothing was filled. "
                            + "Resolve it in the window yourself (sign in, solve the check, or open the form), then ask to continue.");
        }
        FieldMappingPlanner.Plan plan = planner.plan(inspection.fields(), content, resume(applicationId, content), coverNote(applicationId, content));
        PluginResult<FillReport> fill = adapter.fill(live.window(), plan.highConfidence());
        if (fill instanceof PluginResult.Ok<FillReport>(FillReport report) && !report.blockers().isEmpty()) {
            return new AssistView(applicationId, live.attemptId(), State.PAUSED_FOR_YOU, AttemptOutcome.IN_PROGRESS, report.blockers(),
                    inspection.humanSteps(), List.of(), List.of(), List.of(), "Paused: " + report.blockers() + ". Resolve it in the window, then ask to continue.");
        }
        Set<String> filledIds = fill instanceof PluginResult.Ok<FillReport>(FillReport report) ? Set.copyOf(report.filledFieldIds()) : Set.of();
        List<Field> filled = new ArrayList<>();
        List<Field> leftToYou = new ArrayList<>();
        for (FieldMappingPlanner.FieldDecision d : plan.decisions()) {
            if (d.decision() == Decision.FILL && filledIds.contains(d.fieldId())) {
                filled.add(new Field(d.fieldId(), d.label(), d.required(), d.note()));
            } else {
                leftToYou.add(new Field(d.fieldId(), d.label(), d.required(),
                        d.decision() == Decision.FILL ? "could not be filled automatically (or you already filled it)" : d.note()));
            }
        }
        leftToYou.sort(Comparator.comparing(Field::required).reversed());
        List<String> stillEmpty = adapter.validate(live.window()) instanceof PluginResult.Ok<ValidationReport>(ValidationReport v)
                ? v.issues().stream().map(ValidationReport.Issue::path).distinct().toList() : List.of();
        String note = fill instanceof PluginResult.Failed<FillReport> f ? "Filling failed (" + f.message() + "); fill the form yourself. " : "";
        return new AssistView(applicationId, live.attemptId(), State.READY_FOR_YOU, AttemptOutcome.IN_PROGRESS, List.of(),
                inspection.humanSteps(), filled, leftToYou, stillEmpty, note + SUBMIT_YOURSELF);
    }

    /**
     * The form never loaded. A permanent failure (the page is gone) is a definite failure: nothing could be submitted.
     * Anything else leaves the attempt in progress so the same key can reopen the window.
     */
    private AssistView openFailed(LiveSession live, ApplicationAdapter adapter, FailureKind kind, String message) {
        closeWindow(live, adapter);
        if (kind == FailureKind.PERMANENT) {
            AttemptView attempt = applications.recordOutcome(live.applicationId(), live.attemptId(), AttemptOutcome.FAILED,
                    new SubmissionEvidence(SubmissionEvidence.Kind.NONE, "The application page could not be opened: " + message, null),
                    Actor.system());
            return AssistView.of(live.applicationId(), attempt.id(), State.FAILED, attempt.outcome(),
                    "The application page could not be opened (" + message + "). Nothing was submitted; prepare again for a fresh approval.");
        }
        return AssistView.of(live.applicationId(), live.attemptId(), State.WINDOW_NOT_OPEN, AttemptOutcome.IN_PROGRESS,
                "The window could not be opened (" + message + "). Start again with the same Idempotency-Key, or " + reportOutcome());
    }

    private AssistView windowGone(LiveSession live, ApplicationAdapter adapter) {
        closeWindow(live, adapter);
        return AssistView.of(live.applicationId(), live.attemptId(), State.WINDOW_NOT_OPEN, AttemptOutcome.IN_PROGRESS,
                "The window is closed. " + reportOutcome());
    }

    private void closeWindow(LiveSession live, ApplicationAdapter adapter) {
        if (live.window() != null) {
            adapter.close(live.window());
        }
        sessions.release(live);
    }

    private static String reportOutcome() {
        return "Report how it ended: CONFIRMED with evidence, FAILED with what went wrong, or UNCONFIRMED if you are not sure.";
    }

    private LiveSession live(UUID applicationId) {
        UUID owner = UserContext.requireUserId();
        applications.get(applicationId);
        return sessions.find(owner, applicationId).filter(s -> s.window() != null)
                .orElseThrow(() -> new DomainRuleViolationException("No assistance window is open for this application"));
    }

    private PackageContent currentPackage(UUID applicationId) {
        PreparationViews.ApplicationDetail detail = preparation.detail(applicationId);
        if (detail.currentPackage() == null) {
            throw new DomainRuleViolationException("No package has been prepared for this application");
        }
        return detail.currentPackage().content();
    }

    private static ApplicationDestination destination(PackageContent content) {
        String url = content.destination() == null ? null : content.destination().applicationUrl();
        if (url == null) {
            throw new DomainRuleViolationException("The package has no application link");
        }
        try {
            return new ApplicationDestination(URI.create(url), content.destination().sourceKey());
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolationException("The application link is not a valid address");
        }
    }

    private ApplicationAdapter adapter(ApplicationDestination destination) {
        return adapters.find(properties.adapter()).filter(a -> a.supports(destination))
                .orElseThrow(() -> new DomainRuleViolationException("Browser assistance is not available for this application link "
                        + "(adapter '" + properties.adapter() + "'); submit it manually instead"));
    }

    private Optional<Attachment> resume(UUID applicationId, PackageContent content) {
        return content.documents().stream().filter(d -> "RESUME".equals(d.kind()) && "application/pdf".equals(d.mediaType())).findFirst()
                .map(d -> {
                    PreparationViews.Download download = preparation.document(applicationId, d.id());
                    return new Attachment(d.id().toString(), "resume.pdf", download.mediaType(), download.bytes());
                });
    }

    private Optional<String> coverNote(UUID applicationId, PackageContent content) {
        return content.documents().stream().filter(d -> "COVER_NOTE".equals(d.kind())).findFirst()
                .map(d -> new String(preparation.document(applicationId, d.id()).bytes(), StandardCharsets.UTF_8));
    }
}
