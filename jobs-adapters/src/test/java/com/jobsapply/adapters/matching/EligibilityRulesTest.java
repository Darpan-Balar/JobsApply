package com.jobsapply.adapters.matching;

import static com.jobsapply.adapters.matching.MatchFixtures.backendJob;
import static com.jobsapply.adapters.matching.MatchFixtures.backendRole;
import static com.jobsapply.adapters.matching.MatchFixtures.candidate;
import static com.jobsapply.adapters.matching.MatchFixtures.context;
import static com.jobsapply.spi.common.RuleVerdict.FAIL;
import static com.jobsapply.spi.common.RuleVerdict.PASS;
import static com.jobsapply.spi.common.RuleVerdict.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.RuleVerdict;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.RuleOutcome;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Acceptance: every rule is exercised for PASS, FAIL and UNKNOWN. */
class EligibilityRulesTest {

    static final Map<String, EligibilityRule> RULES = EligibilityRules.all().stream()
            .collect(Collectors.toMap(EligibilityRule::ruleType, Function.identity()));
    static final CandidateSnapshot VERIFIED = candidate(VerificationStatus.VERIFIED);
    static final CandidateSnapshot EMPTY = CandidateSnapshot.builder().build();

    private static RuleVerdict verdict(String rule, JobSnapshot job, CandidateSnapshot candidate, RoleProfileDefinition role, Map<String, Object> params) {
        RuleOutcome outcome = RULES.get(rule).evaluate(context(job, candidate, role), params);
        assertThat(outcome.explanation()).as("every outcome is explained").isNotBlank();
        return outcome.verdict();
    }

    private static RuleVerdict verdict(String rule, JobSnapshot job, CandidateSnapshot candidate) {
        return verdict(rule, job, candidate, backendRole(), Map.of());
    }

    @Test
    void elevenRulesAreInstalledAndIdentifiedByType() {
        assertThat(RULES).hasSize(11);
        RULES.forEach((type, rule) -> assertThat(rule.descriptor().id()).isEqualTo(type));
    }

    @Test
    void titleMatch() {
        assertThat(verdict("title-match", backendJob(), EMPTY)).isEqualTo(PASS);
        assertThat(verdict("title-match", backendJob().toBuilder().title("Backend Engineer Intern").build(), EMPTY)).isEqualTo(FAIL);
        assertThat(verdict("title-match", backendJob().toBuilder().title("Product Designer").build(), EMPTY)).isEqualTo(FAIL);
        assertThat(verdict("title-match", backendJob().toBuilder().title("Backend Engineering Manager").build(), EMPTY))
                .as("'engineer' does not match inside 'engineering'").isEqualTo(FAIL);
        assertThat(verdict("title-match", backendJob().toBuilder().title(" ").build(), EMPTY)).isEqualTo(UNKNOWN);
    }

    @Test
    void seniorityIn() {
        assertThat(verdict("seniority-in", backendJob(), EMPTY)).isEqualTo(PASS);
        assertThat(verdict("seniority-in", backendJob().toBuilder().seniority(Seniority.PRINCIPAL).build(), EMPTY)).isEqualTo(FAIL);
        assertThat(verdict("seniority-in", backendJob().toBuilder().seniority(Seniority.UNKNOWN).build(), EMPTY)).isEqualTo(UNKNOWN);
    }

    @Test
    void employmentTypeIn() {
        assertThat(verdict("employment-type-in", backendJob(), EMPTY)).isEqualTo(PASS);
        assertThat(verdict("employment-type-in", backendJob().toBuilder().employmentType(EmploymentType.CONTRACT).build(), EMPTY)).isEqualTo(FAIL);
        assertThat(verdict("employment-type-in", backendJob().toBuilder().employmentType(EmploymentType.UNKNOWN).build(), EMPTY)).isEqualTo(UNKNOWN);
    }

    @Test
    void workplaceTypeIn() {
        assertThat(verdict("workplace-type-in", backendJob(), EMPTY)).isEqualTo(PASS);
        assertThat(verdict("workplace-type-in", backendJob().toBuilder().workplaceType(WorkplaceType.ONSITE).build(), EMPTY)).isEqualTo(FAIL);
        assertThat(verdict("workplace-type-in", backendJob().toBuilder().workplaceType(WorkplaceType.UNKNOWN).build(), EMPTY)).isEqualTo(UNKNOWN);
    }

    @Test
    void locationIn() {
        assertThat(verdict("location-in", backendJob(), EMPTY)).isEqualTo(PASS);
        JobSnapshot us = backendJob().toBuilder().locations(List.of(new RawLocation("NYC", null, null, "US", false))).build();
        assertThat(verdict("location-in", us, EMPTY)).isEqualTo(FAIL);
        JobSnapshot nowhere = backendJob().toBuilder().locations(List.of(new RawLocation("Remote", null, null, null, true))).build();
        assertThat(verdict("location-in", nowhere, EMPTY)).isEqualTo(UNKNOWN);
    }

    @Test
    void minExperienceYears() {
        assertThat(verdict("min-experience-years", backendJob(), VERIFIED, backendRole(), Map.of("years", 5))).isEqualTo(PASS);
        assertThat(verdict("min-experience-years", backendJob(), VERIFIED, backendRole(), Map.of("years", 12))).isEqualTo(FAIL);
        assertThat(verdict("min-experience-years", backendJob(), EMPTY, backendRole(), Map.of("years", 5))).isEqualTo(UNKNOWN);
    }

    @Test
    void maxRequiredExperience() {
        assertThat(verdict("max-required-experience", backendJob(), EMPTY, backendRole(), Map.of("years", 8))).isEqualTo(PASS);
        assertThat(verdict("max-required-experience", backendJob(), EMPTY, backendRole(), Map.of("years", 3))).isEqualTo(FAIL);
        assertThat(verdict("max-required-experience", backendJob().toBuilder().descriptionText("Great team.").build(), EMPTY,
                backendRole(), Map.of("years", 8))).isEqualTo(UNKNOWN);
    }

    @Test
    void educationLevelMin() {
        assertThat(verdict("education-level-min", backendJob(), VERIFIED, backendRole(), Map.of("level", "BACHELOR"))).isEqualTo(PASS);
        assertThat(verdict("education-level-min", backendJob(), VERIFIED, backendRole(), Map.of("level", "MASTER"))).isEqualTo(FAIL);
        assertThat(verdict("education-level-min", backendJob(), EMPTY, backendRole(), Map.of("level", "BACHELOR"))).isEqualTo(UNKNOWN);
        CandidateSnapshot unfinished = VERIFIED.toBuilder().education(List.of(VERIFIED.education().getFirst().toBuilder()
                .level(DegreeLevel.MASTER).completed(false).build())).build();
        assertThat(verdict("education-level-min", backendJob(), unfinished, backendRole(), Map.of("level", "MASTER")))
                .as("an unfinished degree does not count").isEqualTo(FAIL);
    }

    @Test
    void educationFieldIn() {
        assertThat(verdict("education-field-in", backendJob(), VERIFIED, backendRole(), Map.of("fields", List.of("computer science")))).isEqualTo(PASS);
        assertThat(verdict("education-field-in", backendJob(), VERIFIED, backendRole(), Map.of("fields", List.of("physics")))).isEqualTo(FAIL);
        CandidateSnapshot noField = VERIFIED.toBuilder().education(List.of(CandidateEducation.builder().id("e").verification(VerificationStatus.VERIFIED)
                .institution("U").level(DegreeLevel.BACHELOR).completed(true).build())).build();
        assertThat(verdict("education-field-in", backendJob(), noField, backendRole(), Map.of("fields", List.of("physics")))).isEqualTo(UNKNOWN);
    }

    @Test
    void workAuthorization() {
        assertThat(verdict("work-authorization", backendJob(), VERIFIED)).isEqualTo(PASS);
        CandidateSnapshot sponsor = VERIFIED.toBuilder().attributes(List.of(CandidateAttribute.builder().id("a")
                .verification(VerificationStatus.VERIFIED).key("work-authorization.de").value("requires-sponsorship").build())).build();
        assertThat(verdict("work-authorization", backendJob(), sponsor)).isEqualTo(FAIL);
        assertThat(verdict("work-authorization", backendJob(), sponsor, backendRole(), Map.of("allowSponsorship", true))).isEqualTo(PASS);
        assertThat(verdict("work-authorization", backendJob(), EMPTY)).isEqualTo(UNKNOWN);
        JobSnapshot remote = backendJob().toBuilder().locations(List.of(new RawLocation("Remote", null, null, null, true))).build();
        assertThat(verdict("work-authorization", remote, VERIFIED)).isEqualTo(UNKNOWN);
    }

    @Test
    void compensationMin() {
        RoleProfileDefinition wants90k = backendRole().toBuilder().compensation(
                new RoleProfileDefinition.CompensationPreference("EUR", new BigDecimal("90000"), RoleProfileDefinition.CompensationPolicy.TREAT_AS_UNKNOWN)).build();
        RoleProfileDefinition wants150k = wants90k.toBuilder().compensation(
                new RoleProfileDefinition.CompensationPreference("EUR", new BigDecimal("150000"), RoleProfileDefinition.CompensationPolicy.TREAT_AS_UNKNOWN)).build();
        assertThat(verdict("compensation-min", backendJob(), EMPTY, wants90k, Map.of())).isEqualTo(PASS);
        assertThat(verdict("compensation-min", backendJob(), EMPTY, wants150k, Map.of())).isEqualTo(FAIL);
        assertThat(verdict("compensation-min", backendJob().toBuilder().compensation(null).build(), EMPTY, wants90k, Map.of())).isEqualTo(UNKNOWN);
        assertThat(verdict("compensation-min", backendJob().toBuilder()
                .compensation(new Compensation(null, new BigDecimal("50"), "EUR", CompensationPeriod.HOUR, null)).build(), EMPTY, wants90k, Map.of()))
                .as("hourly pay is annualized: 50 * 2080 = 104000").isEqualTo(PASS);
        assertThat(verdict("compensation-min", backendJob().toBuilder()
                .compensation(new Compensation(null, new BigDecimal("200000"), "USD", CompensationPeriod.YEAR, null)).build(), EMPTY, wants90k, Map.of()))
                .as("no currency conversion is guessed").isEqualTo(UNKNOWN);
    }

    @Test
    void unverifiedFactsNeverProduceAPass() {
        CandidateSnapshot unverified = candidate(VerificationStatus.UNVERIFIED);
        assertThat(verdict("min-experience-years", backendJob(), unverified, backendRole(), Map.of("years", 1))).isEqualTo(UNKNOWN);
        assertThat(verdict("education-level-min", backendJob(), unverified, backendRole(), Map.of("level", "BACHELOR"))).isEqualTo(UNKNOWN);
        assertThat(verdict("work-authorization", backendJob(), unverified)).isEqualTo(UNKNOWN);
    }
}
