package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.candidate.MasterResumeService;
import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingSort;
import com.jobsapply.platform.preparation.PackageContent;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationService.QuestionInput;
import com.jobsapply.platform.preparation.PreparationViews;
import com.jobsapply.platform.preparation.PreparationViews.Outcome;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DbAssertions;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.ResumeDocument;
import com.jobsapply.spi.source.AvailabilityObservation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Application preparation end to end with the shipped template and real plugins. */
@IntegrationTest
class PreparationIT {

    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidateImports;
    @Autowired PreparationService preparation;
    @Autowired MasterResumeService masterResumes;
    @Autowired UserService users;
    @Autowired SyncService sync;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;
    @Autowired JdbcTemplate jdbc;

    static final List<QuestionInput> QUESTIONS = List.of(
            new QuestionInput("Will you now or in the future require visa sponsorship?", true),
            new QuestionInput("How many years of professional experience do you have?", true));

    UUID user;
    String candidate;
    DiscoveryFixture.Board board;
    UUID postingId;

    @BeforeEach
    void setUp() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        user = users.createUser(NewUser.builder().username(uniqueKey("p")).displayName("P").build()).id();
        candidate = uniqueKey("main");
        as(() -> candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidate), true, false));
        board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Senior Backend Engineer", "Berlin, Germany")
                .toBuilder().descriptionHtml("<p>Java, Spring Boot, PostgreSQL and Kafka. 5+ years of experience.</p>").build())));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
        postingId = postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 1).items().getFirst().id();
        open();
    }

    private void open() {
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "HTTP 200", Instant.now())));
    }

    private <T> T as(Supplier<T> action) {
        return UserContext.callAs(user, action);
    }

    private PreparationViews.Result prepare(String role) {
        return as(() -> preparation.prepare(postingId, candidate, role, QUESTIONS));
    }

    private PackageContent content(UUID applicationId) {
        return as(() -> preparation.detail(applicationId)).currentPackage().content();
    }

    @Test
    void preparesAReviewablePackageFromVerifiedFactsOnly() {
        PreparationViews.Result result = prepare("backend-engineer");

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        assertThat(result.issues()).noneMatch(i -> i.severity() == PackageIssue.Severity.BLOCKING);
        assertThat(result.issues()).extracting(PackageIssue::code).contains("QUESTIONS_UNAVAILABLE", "ANSWER_REQUIRED");

        PackageContent content = content(result.applicationId());
        assertThat(content.resume().sections()).flatExtracting(ResumeDocument.Section::claims).extracting(Claim::text)
                .contains("Senior Backend Engineer, Beta AG · Jan 2023 – Present", "Built an event pipeline on Kafka processing 2M events per day.");
        assertThat(content.resume().sections()).flatExtracting(ResumeDocument.Section::claims).allSatisfy(c -> assertThat(c.sources()).isNotEmpty());
        assertThat(content.answers()).extracting(a -> a.draft().status())
                .containsExactly(AnswerDraft.Status.NEEDS_USER_INPUT, AnswerDraft.Status.DRAFTED);
        assertThat(content.answers().get(1).draft().origin()).isEqualTo(AnswerDraft.Origin.FACT);
        assertThat(content.unanswered()).extracting(a -> a.question().text()).containsExactly(QUESTIONS.getFirst().text());
        assertThat(content.documents()).extracting(PackageContent.DocumentRef::mediaType).containsExactlyInAnyOrder("text/html", "application/pdf");
        assertThat(content.destination().applicationUrl()).isEqualTo("https://jobs.example/x/apply");

        UUID pdf = content.documents().stream().filter(d -> d.mediaType().equals("application/pdf")).findFirst().orElseThrow().id();
        PreparationViews.Download download = as(() -> preparation.document(result.applicationId(), pdf));
        assertThat(new String(download.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void rePreparingUnchangedInputsIsIdempotent() {
        PreparationViews.Result first = prepare("backend-engineer");
        PreparationViews.Result second = prepare("backend-engineer");
        assertThat(second.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(second.packageVersion()).isEqualTo(first.packageVersion());
    }

    @Test
    void anUnconfirmedPostingCannotBecomeReadyForReview() {
        source.availability(board.externalRef(), "x", ScriptedJobSourceAdapter.failure(com.jobsapply.spi.common.FailureKind.TRANSIENT));
        PreparationViews.Result result = prepare("backend-engineer");
        assertThat(result.status()).isEqualTo(ApplicationStatus.PREPARED);
        assertThat(result.issues()).extracting(PackageIssue::code).contains("AVAILABILITY_UNCONFIRMED");
    }

    @Test
    void notEligibleStopsBeforeAnyDocumentIsGenerated() {
        PreparationViews.Result result = prepare("quant-developer");
        assertThat(result.outcome()).isEqualTo(Outcome.NOT_ELIGIBLE);
        assertThat(result.status()).isEqualTo(ApplicationStatus.NOT_ELIGIBLE);
        assertThat(as(() -> preparation.detail(result.applicationId())).currentPackage()).isNull();
    }

    @Test
    void aClosedPostingExpiresTheApplication() {
        UUID applicationId = prepare("backend-engineer").applicationId();
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.CLOSED, true, "HTTP 404", Instant.now())));
        PreparationViews.Result result = prepare("backend-engineer");
        assertThat(result.outcome()).isEqualTo(Outcome.EXPIRED);
        assertThat(as(() -> preparation.detail(applicationId)).application().status()).isEqualTo(ApplicationStatus.EXPIRED);
    }

    @Test
    void answeringCreatesANewVersionAndKeepsTheDocuments() {
        PreparationViews.Result first = prepare("backend-engineer");
        String sponsorship = content(first.applicationId()).answers().getFirst().question().id();
        List<String> shasBefore = content(first.applicationId()).documents().stream().map(PackageContent.DocumentRef::sha256).toList();

        PreparationViews.Result answered = as(() -> preparation.answer(first.applicationId(), sponsorship, "No"));

        assertThat(answered.outcome()).isEqualTo(Outcome.NEW_VERSION);
        assertThat(answered.packageVersion()).isEqualTo(2);
        assertThat(answered.status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
        PackageContent content = content(first.applicationId());
        assertThat(content.answers().getFirst().draft().origin()).isEqualTo(AnswerDraft.Origin.USER);
        assertThat(content.unanswered()).isEmpty();
        assertThat(content.documents()).extracting(PackageContent.DocumentRef::sha256).containsExactlyInAnyOrderElementsOf(shasBefore);
        assertThat(as(() -> preparation.versions(first.applicationId()))).hasSize(2);
    }

    @Test
    void resumeEditsMustStayTraceable() {
        UUID applicationId = prepare("backend-engineer").applicationId();

        assertThatThrownBy(() -> as(() -> preparation.editResume(applicationId, List.of(new PreparationViews.SectionEdit("Experience",
                List.of(new PreparationViews.ClaimEdit("Built an event pipeline on Kafka processing 20M events per day.", List.of("ach-kafka"), false)))))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("words not found in the cited facts: 20m");

        PreparationViews.Result edited = as(() -> preparation.editResume(applicationId, List.of(
                new PreparationViews.SectionEdit("Summary", List.of(new PreparationViews.ClaimEdit("Backend engineer who enjoys reliable systems.", List.of(), true))),
                new PreparationViews.SectionEdit("Experience", List.of(new PreparationViews.ClaimEdit(
                        "Built an event pipeline on Kafka processing 2M events per day.", List.of("ach-kafka"), false))))));
        assertThat(edited.outcome()).isEqualTo(Outcome.NEW_VERSION);
        assertThat(content(applicationId).resume().sections()).extracting(ResumeDocument.Section::heading).containsExactly("Summary", "Experience");
    }

    @Test
    void theMasterResumeIsNeverModified() {
        byte[] original = "%PDF-1.4\n% Jane's master resume\néÿ binary tail".getBytes(StandardCharsets.ISO_8859_1);
        MasterResumeService.MasterResumeView uploaded = as(() -> masterResumes.upload(candidate, "Jane CV.pdf", "application/pdf", original));
        prepare("backend-engineer");
        UUID applicationId = prepare("backend-engineer").applicationId();
        as(() -> preparation.editResume(applicationId, List.of(new PreparationViews.SectionEdit("Notes",
                List.of(new PreparationViews.ClaimEdit("Edited.", List.of(), true))))));

        MasterResumeService.Content after = as(() -> masterResumes.content(candidate, uploaded.id()));
        assertThat(after.bytes()).isEqualTo(original);
        assertThat(after.meta().sha256()).isEqualTo(JsonCodec.sha256Hex(original)).isEqualTo(uploaded.sha256());
        assertThatThrownBy(() -> as(() -> masterResumes.upload(candidate, "x.pdf", "application/pdf", "not a pdf".getBytes())))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void applicationsAndDocumentsArePrivate() {
        UUID applicationId = prepare("backend-engineer").applicationId();
        UUID document = content(applicationId).documents().getFirst().id();
        UUID other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build()).id();

        assertThatThrownBy(() -> UserContext.callAs(other, () -> preparation.detail(applicationId))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> preparation.document(applicationId, document))).isInstanceOf(NotFoundException.class);
        assertThat(UserContext.callAs(other, () -> preparation.list())).isEmpty();
    }

    @Test
    void rejectingClosesTheApplicationAndAFreshOneCanFollow() {
        UUID first = prepare("backend-engineer").applicationId();
        assertThat(as(() -> preparation.reject(first, "not for me")).status()).isEqualTo(ApplicationStatus.REJECTED_BY_USER);
        assertThat(prepare("backend-engineer").applicationId()).isNotEqualTo(first);
    }

    @Test
    void packagesAreImmutableAndEncryptedAtRest() {
        UUID applicationId = prepare("backend-engineer").applicationId();
        String stored = jdbc.queryForObject("select payload::text from application_package where application_id = ?", String.class, applicationId);
        assertThat(stored).contains("AES-256-GCM").doesNotContain("Jane", "Beta AG", "Kafka");
        DbAssertions.assertRejectedBy(() -> jdbc.update("update application_package set warnings = 0 where application_id = ?", applicationId),
                "rows are immutable");
    }

    @Test
    void unverifiedFactsLeaveTheResumeEmptyAndSayWhy() {
        String unverified = uniqueKey("unv");
        as(() -> candidateImports.importProfile("u.yaml", CandidateYaml.profile(unverified), false, false));
        PreparationViews.Result result = as(() -> preparation.prepare(postingId, unverified, "backend-engineer", List.of()));
        assertThat(result.issues()).extracting(PackageIssue::code).contains("EMPTY_RESUME", "ELIGIBILITY_UNKNOWN");
    }
}
