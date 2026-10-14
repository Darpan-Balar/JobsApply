package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.browser.HumanAtTheWindow;
import com.jobsapply.browser.PlaywrightApplicationAdapter;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionChannel;
import com.jobsapply.platform.application.SubmissionEvidence;
import com.jobsapply.platform.assist.AssistService;
import com.jobsapply.platform.assist.AssistView;
import com.jobsapply.platform.assist.AssistView.State;
import com.jobsapply.platform.assist.internal.AssistSessions;
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
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.submission.SubmissionService;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.application.Blocker;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Browser assistance end to end with the real Playwright adapter (headless) against local fixture forms: prepare,
 * approve, assist, the human submits, the confirmation becomes evidence (ADR-0012).
 */
@IntegrationTest
class AssistIT {

    static HttpServer server;
    static final AtomicInteger POSTS = new AtomicInteger();

    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidateImports;
    @Autowired PreparationService preparation;
    @Autowired SubmissionService submissions;
    @Autowired AssistService assist;
    @Autowired AssistSessions sessions;
    @Autowired PlaywrightApplicationAdapter browser;
    @Autowired UserService users;
    @Autowired SyncService sync;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;

    static final List<QuestionInput> QUESTIONS = List.of(
            new QuestionInput("Will you now or in the future require visa sponsorship?", true),
            new QuestionInput("How many years of professional experience do you have?", true));

    UUID user;
    String candidate;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/submit")) {
                POSTS.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Location", "/submitted.html");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            }
            try (InputStream in = AssistIT.class.getResourceAsStream("/assist" + path)) {
                byte[] body = in == null ? "not found".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(in == null ? 404 : 200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void setUp() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        user = users.createUser(NewUser.builder().username(uniqueKey("a")).displayName("A").build()).id();
        candidate = uniqueKey("main");
        as(() -> candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidate), true, false));
    }

    @AfterEach
    void closeWindows() {
        as(() -> preparation.list()).forEach(a -> assist.closeFor(a.id()));
    }

    private <T> T as(Supplier<T> action) {
        return UserContext.callAs(user, action);
    }

    /** A posting whose application link is {@code path} on the fixture server, prepared and approved. */
    private UUID approved(String path) {
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Senior Backend Engineer", "Berlin, Germany")
                .toBuilder().descriptionHtml("<p>Java, Spring Boot, PostgreSQL and Kafka. 5+ years of experience.</p>")
                .applicationUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path)).build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "HTTP 200", Instant.now())));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
        UUID postingId = postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 1)
                .items().getFirst().id();
        UUID applicationId = as(() -> preparation.prepare(postingId, candidate, "backend-engineer", QUESTIONS)).applicationId();
        String sponsorship = as(() -> preparation.detail(applicationId)).currentPackage().content().answers().getFirst().question().id();
        as(() -> preparation.answer(applicationId, sponsorship, "No"));
        PreparationViews.ApplicationDetail detail = as(() -> preparation.detail(applicationId));
        assertThat(detail.application().status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        as(() -> submissions.approve(applicationId, detail.currentPackage().id()));
        return applicationId;
    }

    private ApplicationStatus status(UUID applicationId) {
        return as(() -> preparation.detail(applicationId)).application().status();
    }

    private String fieldValue(UUID applicationId, String selector) {
        String[] value = new String[1];
        HumanAtTheWindow.act(browser, windowOf(applicationId), page -> value[0] = String.valueOf(page.evaluate(
                "s => { const e = document.querySelector(s); return e.type === 'file' ? Array.from(e.files).map(f => f.name).join(',') "
                        + ": e.type === 'checkbox' ? String(e.checked) : e.value; }", selector)));
        return value[0];
    }

    private UUID windowOf(UUID applicationId) {
        return sessions.find(user, applicationId).orElseThrow().window().id();
    }

    @Test
    void fillsTheApprovedPackageThenRecordsTheConfirmationAfterTheHumanSubmits() {
        UUID applicationId = approved("/apply.html");
        int postsBefore = POSTS.get();

        AssistView view = as(() -> assist.start(applicationId, uniqueKey("idem-key")));

        assertThat(view.state()).isEqualTo(State.READY_FOR_YOU);
        assertThat(view.filled()).extracting(AssistView.Field::fieldId)
                .containsExactlyInAnyOrder("first_name", "last_name", "email", "resume", "question_11", "question_12");
        assertThat(view.leftToYou()).extracting(AssistView.Field::fieldId).containsExactlyInAnyOrder("phone", "gender", "consent");
        assertThat(view.leftToYou()).filteredOn(f -> f.fieldId().equals("gender")).singleElement().extracting(AssistView.Field::note)
                .asString().contains("self-identification");
        assertThat(view.requiredStillEmpty()).containsExactly("consent");
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTING);
        assertThat(fieldValue(applicationId, "#first_name")).isEqualTo("Jane");
        assertThat(fieldValue(applicationId, "#last_name")).isEqualTo("Example");
        assertThat(fieldValue(applicationId, "#resume")).isEqualTo("resume.pdf");
        assertThat(fieldValue(applicationId, "#gender")).isEmpty();
        assertThat(fieldValue(applicationId, "#consent")).isEqualTo("false");
        assertThat(POSTS.get()).as("the platform never submits").isEqualTo(postsBefore);

        AssistView early = as(() -> assist.confirm(applicationId, Duration.ofSeconds(1)));
        assertThat(early.state()).isEqualTo(State.READY_FOR_YOU);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTING);

        HumanAtTheWindow.act(browser, windowOf(applicationId), page -> {
            page.check("#consent");
            page.click("button[type=submit]");
            page.waitForURL("**/submitted.html");
        });
        AssistView confirmed = as(() -> assist.confirm(applicationId, Duration.ofSeconds(5)));

        assertThat(confirmed.state()).isEqualTo(State.CONFIRMED);
        assertThat(POSTS.get()).isEqualTo(postsBefore + 1);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMITTED);
        AttemptView attempt = as(() -> submissions.attempts(applicationId)).getFirst();
        assertThat(attempt.channel()).isEqualTo(SubmissionChannel.BROWSER_ASSISTED);
        assertThat(attempt.evidence().kind()).isEqualTo(SubmissionEvidence.Kind.CONFIRMATION_PAGE);
        assertThat(attempt.evidence().detail()).isEqualTo("Thank you for applying!");
        assertThat(as(() -> assist.screenshot(applicationId, attempt.id()))).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        assertThat(sessions.find(user, applicationId)).as("the window is closed").isEmpty();
    }

    @Test
    void nothingMovesOnToAnotherApplicationAndOnlyOneWindowIsOpenAtATime() {
        UUID first = approved("/apply.html");
        UUID second = approved("/apply.html");
        as(() -> assist.start(first, uniqueKey("idem-key")));

        assertThatThrownBy(() -> as(() -> assist.start(second, uniqueKey("idem-key"))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("one application at a time");
        assertThat(status(second)).as("its approval is untouched").isEqualTo(ApplicationStatus.APPROVED);

        as(() -> assist.close(first));
        assertThat(as(() -> assist.start(second, uniqueKey("idem-key"))).state()).isEqualTo(State.READY_FOR_YOU);
        assertThat(status(first)).isEqualTo(ApplicationStatus.SUBMITTING);
    }

    @Test
    void anUnapprovedApplicationGetsNoWindow() {
        UUID applicationId = approved("/apply.html");
        as(() -> submissions.revoke(applicationId));
        assertThatThrownBy(() -> as(() -> assist.start(applicationId, uniqueKey("idem-key")))).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(sessions.find(user, applicationId)).isEmpty();
    }

    @Test
    void aSignInPagePausesWithoutFillingAnything() {
        UUID applicationId = approved("/login.html");

        AssistView view = as(() -> assist.start(applicationId, uniqueKey("idem-key")));

        assertThat(view.state()).isEqualTo(State.PAUSED_FOR_YOU);
        assertThat(view.blockers()).containsExactly(Blocker.LOGIN_REQUIRED);
        assertThat(view.filled()).isEmpty();
        assertThat(fieldValue(applicationId, "#u")).isEmpty();
        assertThat(as(() -> assist.refresh(applicationId)).state()).isEqualTo(State.PAUSED_FOR_YOU);

        assertThat(as(() -> assist.close(applicationId)).state()).isEqualTo(State.WINDOW_NOT_OPEN);
        assertThat(status(applicationId)).as("only the human knows what happened").isEqualTo(ApplicationStatus.SUBMITTING);
    }

    @Test
    void aMissingApplicationPageIsADefiniteFailure() {
        UUID applicationId = approved("/gone.html");
        AssistView view = as(() -> assist.start(applicationId, uniqueKey("idem-key")));
        assertThat(view.state()).isEqualTo(State.FAILED);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.FAILED);
        assertThat(sessions.find(user, applicationId)).isEmpty();
    }

    @Test
    void replayingTheKeyReopensTheWindowForTheSameAttempt() {
        UUID applicationId = approved("/apply.html");
        String key = uniqueKey("idem-key");
        AssistView first = as(() -> assist.start(applicationId, key));
        as(() -> assist.close(applicationId));

        AssistView again = as(() -> assist.start(applicationId, key));

        assertThat(again.state()).isEqualTo(State.READY_FOR_YOU);
        assertThat(again.attemptId()).isEqualTo(first.attemptId());
        assertThat(as(() -> submissions.attempts(applicationId))).hasSize(1);
        assertThat(as(() -> assist.start(applicationId, uniqueKey("idem-other"))).attemptId())
                .as("while the window is open, starting again only shows it").isEqualTo(first.attemptId());
        assertThat(as(() -> submissions.attempts(applicationId))).hasSize(1);
    }

    @Test
    void reportingTheOutcomeYourselfClosesTheWindow() {
        UUID applicationId = approved("/apply.html");
        AssistView view = as(() -> assist.start(applicationId, uniqueKey("idem-key")));

        as(() -> submissions.reportOutcome(applicationId, view.attemptId(), AttemptOutcome.UNCONFIRMED, null));

        assertThat(sessions.find(user, applicationId)).isEmpty();
        assertThat(as(() -> assist.view(applicationId)).state()).isEqualTo(State.FINISHED);
        assertThat(status(applicationId)).isEqualTo(ApplicationStatus.SUBMISSION_UNCONFIRMED);
    }

    @Test
    void anotherUserCannotSeeOrDriveTheWindow() {
        UUID applicationId = approved("/apply.html");
        as(() -> assist.start(applicationId, uniqueKey("idem-key")));
        UUID other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build()).id();

        assertThatThrownBy(() -> UserContext.callAs(other, () -> assist.view(applicationId))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> assist.confirm(applicationId, Duration.ofSeconds(1))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> assist.close(applicationId))).isInstanceOf(NotFoundException.class);
        assertThat(sessions.find(user, applicationId)).isPresent();
    }
}
