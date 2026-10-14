package com.jobsapply.adapters.matching;

import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import com.jobsapply.spi.profile.RoleProfileDefinition.ScoringConfig;
import com.jobsapply.spi.profile.RoleProfileDefinition.SkillRequirements;
import com.jobsapply.spi.profile.RoleProfileDefinition.Thresholds;
import com.jobsapply.spi.profile.RoleProfileDefinition.TitleRules;
import com.jobsapply.spi.profile.RoleProfileDefinition.WeightedSkill;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Synthetic jobs, candidates and role profiles (illustrative, not anyone's real data). */
final class MatchFixtures {

    static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private MatchFixtures() {}

    static JobSnapshot backendJob() {
        return JobSnapshot.builder()
                .postingId(UUID.randomUUID())
                .employerKey("acme")
                .title("Senior Backend Engineer")
                .seniority(Seniority.SENIOR)
                .employmentType(EmploymentType.FULL_TIME)
                .workplaceType(WorkplaceType.HYBRID)
                .locations(List.of(new RawLocation("Berlin, Germany", "Berlin", null, "DE", false)))
                .compensation(new Compensation(new BigDecimal("80000"), new BigDecimal("100000"), "EUR", CompensationPeriod.YEAR, null))
                .descriptionText("We need 5+ years of experience building Java services with Spring Boot, PostgreSQL and Kafka.")
                .skills(Set.of("java", "spring-boot", "postgresql", "kafka"))
                .build();
    }

    static CandidateSnapshot candidate(VerificationStatus status) {
        return CandidateSnapshot.builder()
                .experiences(List.of(
                        CandidateExperience.builder().id("exp-1").verification(status).employer("Example Co").title("Engineer")
                                .start(YearMonth.of(2019, 1)).end(YearMonth.of(2022, 12)).skills(List.of("java")).build(),
                        CandidateExperience.builder().id("exp-2").verification(status).employer("Example Two").title("Senior Engineer")
                                .start(YearMonth.of(2023, 1)).end(null).skills(List.of("spring-boot", "postgresql")).build()))
                .education(List.of(CandidateEducation.builder().id("edu-1").verification(status).institution("Example University")
                        .level(DegreeLevel.BACHELOR).field("Computer Science").completed(true).build()))
                .skills(List.of(CandidateSkill.builder().id("sk-kafka").verification(status).skill("kafka").build()))
                .attributes(List.of(CandidateAttribute.builder().id("auth-de").verification(status)
                        .key("work-authorization.de").value("citizen").build()))
                .build();
    }

    static RoleProfileDefinition backendRole() {
        return RoleProfileDefinition.builder()
                .key("backend-engineer").displayName("Backend").jobFamily("backend")
                .titles(TitleRules.builder().include(List.of("backend engineer", "software engineer"))
                        .exclude(List.of("intern")).seniority(List.of(Seniority.MID, Seniority.SENIOR)).build())
                .skills(SkillRequirements.builder().required(List.of("java")).requiredAnyOf(List.of(List.of("postgresql", "mysql")))
                        .preferred(List.of(new WeightedSkill("spring-boot", 2), new WeightedSkill("kafka", 1))).build())
                .experience(new RoleProfileDefinition.ExperienceRange(2, 10))
                .locations(new RoleProfileDefinition.LocationPreferences(List.of(WorkplaceType.REMOTE, WorkplaceType.HYBRID),
                        List.of(new RoleProfileDefinition.LocationPreference("DE", null, null))))
                .employmentTypes(List.of(EmploymentType.FULL_TIME))
                .scoring(ScoringConfig.builder().strategy("weighted").strategyVersion(1)
                        .weights(Map.of("title", new BigDecimal("0.2"), "required-skills", new BigDecimal("0.3"),
                                "preferred-skills", new BigDecimal("0.2"), "experience", new BigDecimal("0.15"),
                                "location", new BigDecimal("0.15")))
                        .thresholds(new Thresholds(new BigDecimal("50"), new BigDecimal("70"))).build())
                .build();
    }

    static RoleProfileDefinition quantRole() {
        return backendRole().toBuilder()
                .key("quant-developer").jobFamily("quant-developer")
                .titles(TitleRules.builder().include(List.of("quant developer", "quantitative developer")).build())
                .skills(SkillRequirements.builder().required(List.of("cpp")).requiredAnyOf(List.of(List.of("python", "java")))
                        .preferred(List.of(new WeightedSkill("probability", 2), new WeightedSkill("low-latency", 2))).build())
                .scoring(backendRole().scoring().toBuilder()
                        .weights(Map.of("title", new BigDecimal("0.15"), "required-skills", new BigDecimal("0.35"),
                                "preferred-skills", new BigDecimal("0.25"), "experience", new BigDecimal("0.1"),
                                "location", new BigDecimal("0.15"))).build())
                .build();
    }

    static MatchContext context(JobSnapshot job, CandidateSnapshot candidate, RoleProfileDefinition role) {
        return new MatchContext(job, VERSION, candidate, role, NOW);
    }
}
