package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import com.jobsapply.platform.employer.EmployerPreferenceService;
import com.jobsapply.platform.employer.PreferenceMode;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.matching.MatchRunReport;
import com.jobsapply.platform.matching.MatchView;
import com.jobsapply.platform.matching.MatchingService;
import com.jobsapply.platform.matching.MatchingService.Evaluation;
import com.jobsapply.platform.matching.MatchingService.Outcome;
import com.jobsapply.platform.posting.PostingQuery;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingSort;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.matching.MatchResult;
import com.jobsapply.spi.source.SourceJob;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Matching end to end: discovered postings × imported candidate × shipped role templates, with the real plugins. */
@IntegrationTest
class MatchingIT {

    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidateImports;
    @Autowired CandidateProfileService candidates;
    @Autowired RoleProfileService roleProfiles;
    @Autowired MatchingService matching;
    @Autowired EmployerPreferenceService preferences;
    @Autowired UserService users;
    @Autowired SyncService sync;
    @Autowired ScriptedJobSourceAdapter source;
    @Autowired DiscoveryFixture fixture;
    @Autowired PostingQueryService postings;

    UUID user;
    String candidateKey;
    DiscoveryFixture.Board board;

    @BeforeEach
    void setUp() {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        user = users.createUser(NewUser.builder().username(uniqueKey("m")).displayName("M").build()).id();
        candidateKey = uniqueKey("main");
        UserContext.runAs(user, () -> assertThat(candidateImports.importProfile("me.yaml", CandidateYaml.profile(candidateKey), true, false)
                .issues()).isEmpty());
        board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(
                job("backend", "Senior Backend Engineer", "Build services in Java and Spring Boot on PostgreSQL with Kafka. "
                        + "5+ years of experience required."),
                job("quant", "Quantitative Developer", "Low-latency C++ trading systems; probability and statistics."),
                job("intern", "Backend Engineer Intern", "Learn Java."))));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);
    }

    private static SourceJob job(String id, String title, String description) {
        return DiscoveryFixture.job(id, title, "Berlin, Germany").toBuilder().descriptionHtml("<p>" + description + "</p>").build();
    }

    private UUID posting(String title) {
        return postings.search(PostingQuery.builder().employerKey(board.employerKey()).text(title).build(), PostingSort.TITLE, 0, 10)
                .items().stream().filter(p -> p.title().equals(title)).findFirst().orElseThrow().id();
    }

    private Evaluation evaluate(String title, String role) {
        return UserContext.callAs(user, () -> matching.evaluate(posting(title), candidateKey, role));
    }

    @Test
    void evaluatesAndExplainsWithTheShippedBackendTemplate() {
        Evaluation evaluation = evaluate("Senior Backend Engineer", "backend-engineer");
        MatchView match = evaluation.match();

        assertThat(evaluation.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(match.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(match.score()).isGreaterThan(new BigDecimal("70"));
        assertThat(match.shortlisted()).isTrue();
        assertThat(match.result().matched()).extracting(MatchResult.Requirement::value).contains("java", "postgresql", "spring-boot", "kafka");
        assertThat(match.result().ruleOutcomes()).extracting(r -> r.ruleType())
                .containsExactly("title-match", "employment-type-in", "max-required-experience", "work-authorization");
        assertThat(match.candidateVersion()).isEqualTo(candidateKey + " v1");
    }

    @Test
    void reEvaluationIsIdempotentAndTracksPostingChanges() {
        evaluate("Senior Backend Engineer", "backend-engineer");
        assertThat(evaluate("Senior Backend Engineer", "backend-engineer").outcome()).isEqualTo(Outcome.UNCHANGED);

        source.script(board.externalRef(), List.of(completePage(
                job("backend", "Senior Backend Engineer", "Build services in Java on PostgreSQL. 10+ years of experience required."),
                job("quant", "Quantitative Developer", "C++"), job("intern", "Backend Engineer Intern", "Java"))));
        sync.sync(board.target(), SyncTrigger.MANUAL, false);

        Evaluation changed = evaluate("Senior Backend Engineer", "backend-engineer");
        assertThat(changed.outcome()).isEqualTo(Outcome.UPDATED);
        assertThat(changed.match().eligibility()).as("10+ years exceeds the template's maximum of 8").isEqualTo(Eligibility.NOT_ELIGIBLE);
    }

    @Test
    void differentRoleFamiliesJudgeTheSameJobsDifferently() {
        MatchView quantJobAsBackend = evaluate("Quantitative Developer", "backend-engineer").match();
        MatchView quantJobAsQuant = evaluate("Quantitative Developer", "quant-developer").match();
        MatchView backendJobAsQuant = evaluate("Senior Backend Engineer", "quant-developer").match();

        assertThat(quantJobAsBackend.eligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
        assertThat(quantJobAsQuant.result().ruleOutcomes()).filteredOn(r -> r.ruleType().equals("title-match"))
                .singleElement().satisfies(r -> assertThat(r.verdict().name()).isEqualTo("PASS"));
        assertThat(backendJobAsQuant.eligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
        assertThat(evaluate("Senior Backend Engineer", "backend-engineer").match().score()).isGreaterThan(backendJobAsQuant.score());
    }

    @Test
    void unverifiedFactsCannotMakeACandidateEligible() {
        String unverified = uniqueKey("unv");
        UserContext.runAs(user, () -> candidateImports.importProfile("u.yaml", CandidateYaml.profile(unverified), false, false));
        MatchView match = UserContext.callAs(user, () -> matching.evaluate(posting("Senior Backend Engineer"), unverified, "backend-engineer")).match();

        assertThat(match.eligibility()).isEqualTo(Eligibility.UNKNOWN);
        assertThat(match.result().uncertain()).extracting(MatchResult.UncertainFact::subject).contains("work-authorization");
        UserContext.runAs(user, () -> candidates.verifyFacts(unverified, Set.of(), true));
        MatchView afterVerification = UserContext.callAs(user,
                () -> matching.evaluate(posting("Senior Backend Engineer"), unverified, "backend-engineer")).match();
        assertThat(afterVerification.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(afterVerification.candidateVersion()).as("a new candidate version is a new match").endsWith(" v2");
    }

    @Test
    void runEvaluatesOpenPostingsAndRespectsIgnoredEmployers() {
        MatchRunReport first = UserContext.callAs(user, () -> matching.run(candidateKey, "backend-engineer"));
        assertThat(first.created()).isGreaterThanOrEqualTo(3);
        MatchRunReport second = UserContext.callAs(user, () -> matching.run(candidateKey, "backend-engineer"));
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();

        UserContext.runAs(user, () -> preferences.set(board.employerKey(), PreferenceMode.IGNORE, 50, null));
        MatchRunReport third = UserContext.callAs(user, () -> matching.run(candidateKey, "backend-engineer"));
        assertThat(third.excludedByPreference()).isGreaterThanOrEqualTo(3);

        var best = UserContext.callAs(user, () -> matching.list(candidateKey, "backend-engineer", Eligibility.ELIGIBLE, null, 0, 10));
        assertThat(best.items()).extracting(MatchView::postingTitle).contains("Senior Backend Engineer").doesNotContain("Backend Engineer Intern");
    }

    @Test
    void matchesArePrivateToTheirOwner() {
        UUID matchId = evaluate("Senior Backend Engineer", "backend-engineer").match().id();
        UUID other = users.createUser(NewUser.builder().username(uniqueKey("other")).displayName("O").build()).id();

        assertThatThrownBy(() -> UserContext.callAs(other, () -> matching.get(matchId))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserContext.callAs(other, () -> matching.list(candidateKey, "backend-engineer", null, null, 0, 10)))
                .as("other user has no such candidate profile").isInstanceOf(NotFoundException.class);
    }

    @Test
    void forkedTemplatesArePrivateCopies() {
        UserContext.runAs(user, () -> roleProfiles.forkTemplate("data-engineer"));
        UserContext.runAs(user, () -> assertThat(roleProfiles.findVisible("data-engineer")).get()
                .satisfies(p -> assertThat(p.systemTemplate()).isFalse()));
    }
}
