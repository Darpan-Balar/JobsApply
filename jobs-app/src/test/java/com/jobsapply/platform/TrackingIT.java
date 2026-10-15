package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionEvidence;
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
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.submission.SubmissionService;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.platform.tracking.InterviewKind;
import com.jobsapply.platform.tracking.InterviewStatus;
import com.jobsapply.platform.tracking.TrackingService;
import com.jobsapply.platform.tracking.TrackingViews.ContactInput;
import com.jobsapply.platform.tracking.TrackingViews.ContactView;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpInput;
import com.jobsapply.platform.tracking.TrackingViews.FollowUpView;
import com.jobsapply.platform.tracking.TrackingViews.InterviewInput;
import com.jobsapply.platform.tracking.TrackingViews.InterviewView;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Contacts, interviews and follow-ups: private, encrypted, and tied to the user's own applications (ADR-0013). */
@IntegrationTest
class TrackingIT {

    @Autowired TrackingService tracking;
    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidateImports;
    @Autowired PreparationService preparation;
    @Autowired SubmissionService submissions;
    @Autowired UserService users;
    @Autowired SyncService sync;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;
    @Autowired JdbcTemplate jdbc;

    UUID user;
    UUID other;

    @BeforeEach
    void setUp() {
        user = users.createUser(NewUser.builder().username(uniqueKey("t")).displayName("T").build()).id();
        other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build()).id();
    }

    private <T> T as(Supplier<T> action) {
        return UserContext.callAs(user, action);
    }

    private static ContactInput contact(String name) {
        return new ContactInput(name, "recruiter@example.org", "+49 30 1234567", "Recruiter", null, "Met at a meetup", null);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    /** A real submitted application: prepared, approved, submitted, confirmed with evidence. */
    private UUID submitted() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        String candidate = uniqueKey("main");
        as(() -> candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidate), true, false));
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Senior Backend Engineer", "Berlin, Germany")
                .toBuilder().descriptionHtml("<p>Java, Spring Boot, PostgreSQL and Kafka. 5+ years of experience.</p>").build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "HTTP 200", Instant.now())));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
        UUID postingId = postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 1)
                .items().getFirst().id();
        UUID applicationId = as(() -> preparation.prepare(postingId, candidate, "backend-engineer",
                List.of(new QuestionInput("How many years of professional experience do you have?", true)))).applicationId();
        as(() -> submissions.approve(applicationId, as(() -> preparation.detail(applicationId)).currentPackage().id()));
        UUID attempt = as(() -> submissions.start(applicationId, uniqueKey("idem-key"))).attempt().id();
        as(() -> submissions.reportOutcome(applicationId, attempt, AttemptOutcome.CONFIRMED,
                new SubmissionEvidence(SubmissionEvidence.Kind.CONFIRMATION_EMAIL, "Application received", null)));
        return applicationId;
    }

    @Test
    void contactsAreEncryptedAtRestAndPrivate() {
        ContactView created = as(() -> tracking.createContact(contact("Rita Recruiter")));

        assertThat(as(() -> tracking.contact(created.id())).name()).isEqualTo("Rita Recruiter");
        assertThat(jdbc.queryForObject("select details::text from contact where id = ?", String.class, created.id()))
                .doesNotContain("Rita", "recruiter@example.org", "meetup");
        assertThatThrownBy(() -> UserContext.callAs(other, () -> tracking.contact(created.id()))).isInstanceOf(NotFoundException.class);
        assertThat(UserContext.callAs(other, () -> tracking.contacts())).isEmpty();

        ContactView updated = as(() -> tracking.updateContact(created.id(), contact("Rita R.")));
        assertThat(updated.name()).isEqualTo("Rita R.");
        assertThatThrownBy(() -> as(() -> tracking.createContact(new ContactInput("x", null, null, null, null, null, UUID.randomUUID()))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("does not exist");
    }

    @Test
    void interviewsBelongToSubmittedApplicationsOnly() {
        UUID applicationId = submitted();
        ContactView interviewer = as(() -> tracking.createContact(contact("Ian Interviewer")));
        InterviewInput input = new InterviewInput(Instant.now().plus(Duration.ofDays(3)), 45, InterviewKind.TECHNICAL, "Video call",
                "Prepare system design", List.of(interviewer.id()));

        InterviewView interview = as(() -> tracking.scheduleInterview(applicationId, input));

        assertThat(interview.status()).isEqualTo(InterviewStatus.SCHEDULED);
        assertThat(interview.contactIds()).containsExactly(interviewer.id());
        assertThat(as(() -> tracking.interviews(applicationId))).extracting(InterviewView::id).containsExactly(interview.id());
        assertThat(jdbc.queryForObject("select details::text from interview where id = ?", String.class, interview.id()))
                .doesNotContain("system design");
        assertThat(as(() -> tracking.markInterview(interview.id(), InterviewStatus.COMPLETED)).status()).isEqualTo(InterviewStatus.COMPLETED);

        ContactView foreign = UserContext.callAs(other, () -> tracking.createContact(contact("Not Mine")));
        assertThatThrownBy(() -> as(() -> tracking.scheduleInterview(applicationId, new InterviewInput(Instant.now(), 30,
                InterviewKind.OTHER, null, null, List.of(foreign.id()))))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> tracking.scheduleInterview(applicationId, input)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anApplicationThatWasNotSubmittedGetsNoInterview() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        String candidate = uniqueKey("main");
        as(() -> candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidate), true, false));
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Senior Backend Engineer", "Berlin, Germany")
                .toBuilder().descriptionHtml("<p>Java and Kafka.</p>").build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "HTTP 200", Instant.now())));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
        UUID postingId = postings.search(PostingQuery.builder().employerKey(board.employerKey()).build(), PostingSort.TITLE, 0, 1)
                .items().getFirst().id();
        UUID applicationId = as(() -> preparation.prepare(postingId, candidate, "backend-engineer", List.of())).applicationId();

        assertThatThrownBy(() -> as(() -> tracking.scheduleInterview(applicationId,
                new InterviewInput(Instant.now(), 30, InterviewKind.PHONE_SCREEN, null, null, List.of()))))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("submitted applications only");
    }

    @Test
    void followUpsReportOverdueAndSurviveTheirContact() {
        ContactView contact = as(() -> tracking.createContact(contact("Fiona Follow")));
        FollowUpView overdue = as(() -> tracking.createFollowUp(new FollowUpInput(today().minusDays(2), "Chase the recruiter", null, contact.id())));
        FollowUpView soon = as(() -> tracking.createFollowUp(new FollowUpInput(today().plusDays(3), "Send thank-you note", null, null)));
        FollowUpView later = as(() -> tracking.createFollowUp(new FollowUpInput(today().plusDays(30), "Check status", null, null)));

        assertThat(overdue.overdue()).isTrue();
        assertThat(soon.overdue()).isFalse();
        var summary = as(() -> tracking.summary());
        assertThat(summary.openFollowUps()).extracting(FollowUpView::id).containsExactly(overdue.id(), soon.id());
        assertThat(summary.overdueFollowUps()).isEqualTo(1);
        assertThat(as(() -> tracking.followUps(false, null))).extracting(FollowUpView::id).contains(later.id());

        assertThat(as(() -> tracking.completeFollowUp(overdue.id())).done()).isTrue();
        assertThat(as(() -> tracking.summary()).overdueFollowUps()).isZero();
        assertThat(as(() -> tracking.followUps(false, null))).extracting(FollowUpView::id).doesNotContain(overdue.id());
        assertThat(as(() -> tracking.reopenFollowUp(overdue.id())).done()).isFalse();

        as(() -> {
            tracking.deleteContact(contact.id());
            return null;
        });
        assertThat(as(() -> tracking.followUps(true, null))).filteredOn(f -> f.id().equals(overdue.id())).singleElement()
                .satisfies(f -> {
                    assertThat(f.contactId()).isNull();
                    assertThat(f.note()).isEqualTo("Chase the recruiter");
                });
        assertThat(UserContext.callAs(other, () -> tracking.followUps(true, null))).isEmpty();
        assertThatThrownBy(() -> UserContext.callAs(other, () -> tracking.completeFollowUp(soon.id()))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anUpcomingInterviewAppearsInTheSummary() {
        UUID applicationId = submitted();
        InterviewView soon = as(() -> tracking.scheduleInterview(applicationId,
                new InterviewInput(Instant.now().plus(Duration.ofDays(2)), 60, InterviewKind.ONSITE, null, null, List.of())));
        as(() -> tracking.scheduleInterview(applicationId,
                new InterviewInput(Instant.now().plus(Duration.ofDays(40)), 60, InterviewKind.FINAL, null, null, List.of())));

        assertThat(as(() -> tracking.summary()).upcomingInterviews()).extracting(InterviewView::id).containsExactly(soon.id());
    }
}
