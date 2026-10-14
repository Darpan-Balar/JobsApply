package com.jobsapply.adapters.matching;

import static com.jobsapply.adapters.matching.MatchFixtures.backendJob;
import static com.jobsapply.adapters.matching.MatchFixtures.backendRole;
import static com.jobsapply.adapters.matching.MatchFixtures.candidate;
import static com.jobsapply.adapters.matching.MatchFixtures.context;
import static com.jobsapply.adapters.matching.MatchFixtures.quantRole;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.matching.MatchResult;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedMatchingStrategyTest {

    final WeightedMatchingStrategy strategy = new WeightedMatchingStrategy(EligibilityRules.all(), ScoreComponents.all());

    private RoleProfileDefinition withRules(RoleProfileDefinition role, String... types) {
        return role.toBuilder().eligibility(Arrays.stream(types)
                .map(t -> new RoleProfileDefinition.RuleSpec(t, null)).toList()).build();
    }

    @Test
    void scoresAndExplainsAStrongMatch() {
        MatchResult result = strategy.evaluate(context(backendJob(), candidate(VerificationStatus.VERIFIED),
                withRules(backendRole(), "title-match", "employment-type-in", "work-authorization")));

        assertThat(result.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(result.strategyKey()).isEqualTo("weighted");
        assertThat(result.strategyVersion()).isEqualTo(1);
        assertThat(result.matched()).extracting(MatchResult.Requirement::value).contains("java", "postgresql", "spring-boot", "kafka");
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("100% of the weighted evidence"));
    }

    @Test
    void theSameJobScoresDifferentlyForDifferentRoleFamilies() {
        CandidateSnapshot candidate = candidate(VerificationStatus.VERIFIED);
        MatchResult backend = strategy.evaluate(context(backendJob(), candidate, withRules(backendRole(), "title-match")));
        MatchResult quant = strategy.evaluate(context(backendJob(), candidate, withRules(quantRole(), "title-match")));

        assertThat(backend.score()).isGreaterThan(quant.score());
        assertThat(backend.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(quant.eligibility()).as("a backend title fails the quant title rule").isEqualTo(Eligibility.NOT_ELIGIBLE);
    }

    @Test
    void missingEvidenceIsExcludedNotGuessedAndIsReportedAsUncertain() {
        CandidateSnapshot unverified = candidate(VerificationStatus.UNVERIFIED);
        MatchResult result = strategy.evaluate(context(backendJob(), unverified,
                withRules(backendRole(), "title-match", "work-authorization", "min-experience-years")));

        assertThat(result.eligibility()).as("unknown is never treated as a pass").isEqualTo(Eligibility.UNKNOWN);
        assertThat(result.uncertain()).extracting(MatchResult.UncertainFact::subject)
                .contains("work-authorization", "min-experience-years", "experience");
        assertThat(result.componentScores()).filteredOn(s -> s.component().equals("experience"))
                .singleElement().satisfies(s -> assertThat(s.value()).isNull());
        assertThat(result.missing()).extracting(MatchResult.Requirement::value).as("unverified skills do not count").contains("java");
        assertThat(result.reasons()).anySatisfy(r -> assertThat(r).contains("% of the weighted evidence"));
    }

    @Test
    void allRulesAreEvaluatedForACompleteExplanation() {
        MatchResult result = strategy.evaluate(context(backendJob().toBuilder().title("Backend Engineer Intern").build(),
                candidate(VerificationStatus.VERIFIED), withRules(backendRole(), "title-match", "employment-type-in", "seniority-in")));
        assertThat(result.eligibility()).isEqualTo(Eligibility.NOT_ELIGIBLE);
        assertThat(result.ruleOutcomes()).hasSize(3);
    }

    @Test
    void uninstalledRuleIsUnknownNotPass() {
        MatchResult result = strategy.evaluate(context(backendJob(), candidate(VerificationStatus.VERIFIED), withRules(backendRole(), "no-such-rule")));
        assertThat(result.eligibility()).isEqualTo(Eligibility.UNKNOWN);
    }

    @Test
    void resultIsDeterministic() {
        var ctx = context(backendJob(), candidate(VerificationStatus.VERIFIED), withRules(backendRole(), "title-match"));
        assertThat(strategy.evaluate(ctx)).isEqualTo(strategy.evaluate(ctx));
    }

    @Test
    void experienceYearsMergeOverlapsAndOnlyUseUsableFacts() {
        CandidateSnapshot candidate = candidate(VerificationStatus.VERIFIED);
        assertThat(CandidateFacts.totalExperienceYears(candidate, YearMonth.of(2026, 10)))
                .contains(new BigDecimal("7.8"));
        assertThat(CandidateFacts.totalExperienceYears(candidate(VerificationStatus.UNVERIFIED), YearMonth.of(2026, 10)))
                .isEmpty();
        assertThat(List.of()).isEmpty();
    }
}
