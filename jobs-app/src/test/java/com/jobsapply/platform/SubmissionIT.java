package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.ApplicationService.ApprovalView;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionEvidence;
import com.jobsapply.platform.audit.AuditService;
import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingSort;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationService.QuestionInput;
import com.jobsapply.platform.preparation.PreparationViews;
import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.submission.SubmissionService;
import com.jobsapply.platform.submission.internal.SubmissionRecovery;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Approval and submission workflow (ADR-0006) end to end: gates, single use, idempotency, evidence, recovery, races. */
@IntegrationTest
class SubmissionIT {

    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidateImports;
    @Autowired PreparationService preparation;
    @Autowired SubmissionService submissions;
    @Autowired SubmissionRecovery recovery;
    @Autowired AuditService audit;
    @Autowired UserService users;
    @Autowired SyncService sync;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;
    @Autowired JdbcTemplate jdbc;

    static final QuestionInput EXPERIENCE = new QuestionInput("How many years of professional experience do you have?", true);
    static final QuestionInput SPONSORSHIP = new QuestionInput("Will you now or in the future require visa sponsorship?", true);
    static final String CONFIRMATION = "Thank you Jane Doe, your application REF-4711 was received";
    static final SubmissionEvidence EVIDENCE = new SubmissionEvidence(SubmissionEvidence.Kind.CONFIRMATION_PAGE, CONFIRMATION,
            "https://jobs.example/x/thanks");

    UUID user;
    String candidate;
    UUID postingId;

    @BeforeEach
    void setUp() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        user = users.createUser(NewUser.builder().username(uniqueKey("s")).displayName("S").build()).id();
        candidate = uniqueKey("main");
        as(() -> candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidate), true, false));
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Senior Backend Engineer", "Berlin, Germany")
                .toBuilder().descriptionHtml("<p>Java, Spring Boot, PostgreSQL and Kafka. 5+ years of experience.</p>").build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "HTTP 200", Instant.now())));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
        postingId = postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 1).items().getFirst().id();
    }

    private <T> T as(Supplier<T> action) {
        return UserContext.callAs(user, action);
    }

    private PreparationViews.Result prepare(String candidateKey, List<QuestionInput> questions) {
        PreparationViews.Result result = as(() -> preparation.prepare(postingId, candidateKey, "backend-engineer", questions));
        assertThat(result.status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        return result;
    }

    /** A complete, approvable application. */
    private UUID ready() {
        return prepare(candidate, List.of(EXPERIENCE)).applicationId();
    }

    private UUID currentPackage(UUID applicationId) {
        return as(() -> preparation.detail(applicationId)).currentPackage().id();
    }

    private ApplicationStatus status(UUID applicationId) {
        return as(() -> preparation.detail(applicationId)).application().status();
    }

    private AttemptView startApproved(UUID applicationId) {
        as(() -> submissions.approve(applicationId, currentPackage(applicationId)));
        return as(() -> submissions.start(applicationId, uniqueKey("idem-key"))).attempt();
    }

    @Test
    void approvalIsRefusedWhileARequiredQuestionIsUnanswered() {
        UUID applicationId = prepare(candidate, List.of(SPONSORSHIP, EXPERIENCE)).applicationId();
        UUID packageId = currentPackage(applicationId);

        assertThat(as(() -> submissions.approvalBlockers(applicationId))).singleElement().asString().contains("required question unanswered");
        assertThatThrownBy(() -> as(() -> submissions.approve(applicationId, packageId)))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("visa sponsorship");

        String question = as(() -> preparation.detail(applicationId)).currentPackage().content().answers().getFirst().question().id();
        as(() -> preparation.answer(applicationId, question, "No"));
        assertThatThrownBy(() -> as(() -> submissions.approve(applicationId, packageId)))
                .as("the reviewed package is no longer current").isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("not the current one");

        ApprovalView approval = as(() -> submissions.approve(applicationId, currentPackage(applicationId)));
        assertThat(approval.packageHash()).isEqualTo(as(() -> preparation.detail(applicationId)).currentPackage().contentHash());
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void theHappyPathNeedsEvidenceAndTheApprovalIsSingleUse() {
        UUID applicationId = ready();
        as(() -> submissions.approve(applicationId, currentPackage(applicationId)));
        String key = uniqueKey("idem-key");

        SubmissionService.StartedSubmission started = as(() -> submissions.start(applicationId, key));
        assertThat(started.applicationUrl()).isEqualTo("https://jobs.example/x/apply");
        assertThat(started.attempt().outcome()).isEqualTo(AttemptOutcome.IN_PROGRESS);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTING);
        assertThat(as(() -> submissions.start(applicationId, key)).attempt().id()).as("replay").isEqualTo(started.attempt().id());
        assertThatThrownBy(() -> as(() -> submissions.start(applicationId, uniqueKey("idem-other"))))
                .as("the approval was consumed").isInstanceOf(InvalidStateTransitionException.class);

        UUID attemptId = started.attempt().id();
        assertThatThrownBy(() -> as(() -> submissions.reportOutcome(applicationId, attemptId, AttemptOutcome.CONFIRMED, null)))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("needs evidence");
        assertThatThrownBy(() -> as(() -> submissions.reportOutcome(applicationId, attemptId, AttemptOutcome.CONFIRMED,
                new SubmissionEvidence(SubmissionEvidence.Kind.NONE, "I think so", null)))).isInstanceOf(DomainRuleViolationException.class);

        AttemptView confirmed = as(() -> submissions.reportOutcome(applicationId, attemptId, AttemptOutcome.CONFIRMED, EVIDENCE));
        assertThat(confirmed.outcome()).isEqualTo(AttemptOutcome.CONFIRMED);
        assertThat(confirmed.evidence().detail()).isEqualTo(CONFIRMATION);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(jdbc.queryForObject("select evidence::text from application_attempt where id = ?", String.class, attemptId))
                .as("evidence is encrypted at rest").doesNotContain("Jane", "REF-4711");
        assertThatThrownBy(() -> as(() -> preparation.prepare(postingId, candidate, "backend-engineer", List.of(EXPERIENCE))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("SUBMITTED");

        assertThat(as(() -> submissions.withdraw(applicationId)).status()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(as(() -> submissions.attempts(applicationId))).singleElement().extracting(AttemptView::id).isEqualTo(attemptId);
    }

    @Test
    void changingThePackageRevokesTheApproval() {
        UUID applicationId = ready();
        ApprovalView approval = as(() -> submissions.approve(applicationId, currentPackage(applicationId)));

        as(() -> preparation.editResume(applicationId, List.of(new PreparationViews.SectionEdit("Summary",
                List.of(new PreparationViews.ClaimEdit("Backend engineer who enjoys reliable systems.", List.of(), true))))));

        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        assertThat(as(() -> submissions.approvals(applicationId))).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(approval.id());
            assertThat(a.revokedAt()).isNotNull();
            assertThat(a.consumedAt()).isNull();
        });
        assertThatThrownBy(() -> as(() -> submissions.start(applicationId, uniqueKey("idem-key"))))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void revokingReturnsToReview() {
        UUID applicationId = ready();
        as(() -> submissions.approve(applicationId, currentPackage(applicationId)));
        assertThat(as(() -> submissions.revoke(applicationId)).status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        assertThat(as(() -> submissions.approvals(applicationId))).singleElement().extracting(ApprovalView::revokedAt).isNotNull();
        assertThatThrownBy(() -> as(() -> submissions.revoke(applicationId))).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void aFailedSubmissionNeedsReviewAndAFreshApproval() {
        UUID applicationId = ready();
        AttemptView attempt = startApproved(applicationId);
        assertThatThrownBy(() -> as(() -> submissions.reportOutcome(applicationId, attempt.id(), AttemptOutcome.FAILED, null)))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("Describe what failed");
        as(() -> submissions.reportOutcome(applicationId, attempt.id(), AttemptOutcome.FAILED,
                new SubmissionEvidence(SubmissionEvidence.Kind.NONE, "The form rejected the upload", null)));
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.FAILED);
        assertThatThrownBy(() -> as(() -> submissions.start(applicationId, uniqueKey("idem-key")))).isInstanceOf(InvalidStateTransitionException.class);

        PreparationViews.Result again = as(() -> preparation.prepare(postingId, candidate, "backend-engineer", List.of(EXPERIENCE)));
        assertThat(again.outcome()).isEqualTo(PreparationViews.Outcome.UNCHANGED);
        assertThat(again.status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);

        AttemptView second = startApproved(applicationId);
        assertThat(second.approvalId()).isNotEqualTo(attempt.approvalId());
        assertThat(as(() -> submissions.attempts(applicationId))).hasSize(2);
    }

    @Test
    void anInterruptedSubmissionIsNeverRetriedUntilTheCandidateVerifiesIt() {
        UUID applicationId = ready();
        AttemptView attempt = startApproved(applicationId);

        recovery.recover();

        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMISSION_UNCONFIRMED);
        assertThat(as(() -> submissions.attempts(applicationId))).singleElement().extracting(AttemptView::outcome).isEqualTo(AttemptOutcome.UNCONFIRMED);
        assertThatThrownBy(() -> as(() -> preparation.prepare(postingId, candidate, "backend-engineer", List.of(EXPERIENCE))))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> as(() -> submissions.approve(applicationId, attempt.packageId()))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> as(() -> submissions.start(applicationId, uniqueKey("idem-key")))).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> as(() -> submissions.reportOutcome(applicationId, attempt.id(), AttemptOutcome.CONFIRMED, EVIDENCE)))
                .as("only verification resolves it").isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> as(() -> submissions.verify(applicationId, AttemptOutcome.CONFIRMED, null)))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("needs evidence");

        AttemptView verified = as(() -> submissions.verify(applicationId, AttemptOutcome.CONFIRMED,
                new SubmissionEvidence(SubmissionEvidence.Kind.CONFIRMATION_EMAIL, "Application received", null)));
        assertThat(verified.verifiedAt()).isNotNull();
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    void anUncertainOutcomeIsReportedAsUnconfirmedAndCanBeVerifiedAsFailed() {
        UUID applicationId = ready();
        AttemptView attempt = startApproved(applicationId);
        as(() -> submissions.reportOutcome(applicationId, attempt.id(), AttemptOutcome.UNCONFIRMED, null));
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMISSION_UNCONFIRMED);

        as(() -> submissions.verify(applicationId, AttemptOutcome.FAILED,
                new SubmissionEvidence(SubmissionEvidence.Kind.EMPLOYER_PORTAL, "The portal lists no application", null)));
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.FAILED);
    }

    @Test
    void idempotencyKeysAreValidatedAndBoundToOneApplication() {
        UUID first = ready();
        as(() -> submissions.approve(first, currentPackage(first)));
        assertThatThrownBy(() -> as(() -> submissions.start(first, "short"))).isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> as(() -> submissions.start(first, null))).isInstanceOf(DomainRuleViolationException.class);
        String key = uniqueKey("idem-key");
        as(() -> submissions.start(first, key));

        String other = uniqueKey("second");
        as(() -> candidateImports.importProfile("second.yaml", CandidateYaml.profile(other), true, false));
        UUID second = prepare(other, List.of(EXPERIENCE)).applicationId();
        assertThatThrownBy(() -> as(() -> submissions.start(second, key))).isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("another application");
    }

    @Test
    void onlyOneLiveApplicationPerVacancyAcrossCandidateProfiles() {
        UUID first = ready();
        String other = uniqueKey("second");
        as(() -> candidateImports.importProfile("second.yaml", CandidateYaml.profile(other), true, false));
        UUID second = prepare(other, List.of(EXPERIENCE)).applicationId();

        AttemptView attempt = startApproved(first);
        assertThatThrownBy(() -> as(() -> submissions.approve(second, currentPackage(second))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("already have");
        assertThat(status(second)).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);

        as(() -> submissions.reportOutcome(first, attempt.id(), AttemptOutcome.CONFIRMED, EVIDENCE));
        as(() -> submissions.withdraw(first));
        assertThat(as(() -> submissions.approve(second, currentPackage(second))).consumedAt()).as("withdrawn is no longer live").isNull();
    }

    @Test
    void parallelApprovalsProduceExactlyOneApproval() throws Exception {
        UUID applicationId = ready();
        UUID packageId = currentPackage(applicationId);

        List<Object> results = race(4, () -> as(() -> submissions.approve(applicationId, packageId)));

        assertThat(results).filteredOn(ApprovalView.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(Throwable.class::isInstance).hasSize(3).allSatisfy(e -> assertThat(e).isInstanceOfAny(
                ConcurrencyConflictException.class, OptimisticLockingFailureException.class, InvalidStateTransitionException.class));
        assertThat(jdbc.queryForObject("select count(*) from submission_approval where application_id = ?", Long.class, applicationId)).isEqualTo(1);
    }

    @Test
    void parallelStartsProduceExactlyOneAttempt() throws Exception {
        UUID applicationId = ready();
        as(() -> submissions.approve(applicationId, currentPackage(applicationId)));

        List<Object> results = race(4, () -> as(() -> submissions.start(applicationId, uniqueKey("idem-key"))));

        assertThat(results).filteredOn(SubmissionService.StartedSubmission.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(Throwable.class::isInstance).hasSize(3).allSatisfy(e -> assertThat(e).isInstanceOfAny(
                ConcurrencyConflictException.class, OptimisticLockingFailureException.class, InvalidStateTransitionException.class));
        assertThat(jdbc.queryForObject("select count(*) from application_attempt where application_id = ?", Long.class, applicationId)).isEqualTo(1);
    }

    @Test
    void parallelReplaysOfOneKeyNeverCreateASecondAttempt() throws Exception {
        UUID applicationId = ready();
        as(() -> submissions.approve(applicationId, currentPackage(applicationId)));
        String key = uniqueKey("idem-key");

        List<Object> results = race(4, () -> as(() -> submissions.start(applicationId, key)));

        assertThat(results).filteredOn(SubmissionService.StartedSubmission.class::isInstance).isNotEmpty()
                .extracting(r -> ((SubmissionService.StartedSubmission) r).attempt().id()).containsOnly(
                        ((SubmissionService.StartedSubmission) results.stream().filter(SubmissionService.StartedSubmission.class::isInstance)
                                .findFirst().orElseThrow()).attempt().id());
        assertThat(jdbc.queryForObject("select count(*) from application_attempt where application_id = ?", Long.class, applicationId)).isEqualTo(1);
    }

    @Test
    void theAuditTrailIsPrivateAndFreeOfPersonalData() {
        UUID applicationId = ready();
        AttemptView attempt = startApproved(applicationId);
        as(() -> submissions.reportOutcome(applicationId, attempt.id(), AttemptOutcome.CONFIRMED, EVIDENCE));

        List<AuditService.AuditView> trail = as(() -> audit.mine(applicationId, 100));
        assertThat(trail).extracting(AuditService.AuditView::eventType)
                .contains("approval-granted", "submission-started", "submission-finished", "status-changed", "package-created");
        List<String> payloads = jdbc.queryForList("select payload::text from audit_event where subject_user_id = ?", String.class, user);
        assertThat(payloads).isNotEmpty().allSatisfy(p -> assertThat(p).doesNotContain("Jane", "REF-4711", "jobs.example"));

        UUID other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build()).id();
        assertThat(UserContext.callAs(other, () -> audit.mine(applicationId, 100))).isEmpty();
    }

    @Test
    void anotherUserCannotTouchTheWorkflow() {
        UUID applicationId = ready();
        UUID packageId = currentPackage(applicationId);
        UUID other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build()).id();

        assertThatThrownBy(() -> UserContext.callAs(other, () -> submissions.approve(applicationId, packageId))).isInstanceOf(NotFoundException.class);
        as(() -> submissions.approve(applicationId, packageId));
        assertThatThrownBy(() -> UserContext.callAs(other, () -> submissions.start(applicationId, uniqueKey("idem-key"))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> submissions.approvals(applicationId))).isInstanceOf(NotFoundException.class);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.APPROVED);
    }

    /** Runs {@code n} copies of the action at once; returns each result or the exception it threw. */
    private static List<Object> race(int n, Callable<Object> action) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Object> results = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        results.add(action.call());
                    } catch (Exception e) {
                        results.add(e);
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }
        return results;
    }
}
