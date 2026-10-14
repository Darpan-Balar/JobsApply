package com.jobsapply.platform.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.preparation.PackageContent.Answer;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue;
import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.Claim;
import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Acceptance: a fabricated claim fails validation, even when it cites a real fact. */
class TraceabilityValidatorTest {

    static final UUID V = UUID.randomUUID();
    final TraceabilityValidator validator = new TraceabilityValidator();
    final CandidateSnapshot snapshot = CandidateSnapshot.builder()
            .contact(CandidateContact.builder().fullName("Jane Example").links(List.of(URI.create("https://github.com/jane"))).build())
            .experiences(List.of(CandidateExperience.builder().id("exp").verification(VerificationStatus.VERIFIED).employer("Now GmbH")
                    .title("Senior Engineer").start(YearMonth.of(2020, 1)).skills(List.of("kafka")).build()))
            .achievements(List.of(
                    CandidateAchievement.builder().id("ach").verification(VerificationStatus.VERIFIED)
                            .text("Built a Kafka pipeline handling 2M events/day.").experienceId("exp").build(),
                    CandidateAchievement.builder().id("unv").verification(VerificationStatus.UNVERIFIED).text("Led 40 engineers.").build()))
            .education(List.of(CandidateEducation.builder().id("edu").verification(VerificationStatus.VERIFIED).institution("Example U")
                    .level(DegreeLevel.BACHELOR).field("Computer Science").start(YearMonth.of(2013, 9)).end(YearMonth.of(2016, 6)).completed(true).build()))
            .skills(List.of(CandidateSkill.builder().id("sk").verification(VerificationStatus.VERIFIED).skill("spring-boot").years(new BigDecimal("4")).build()))
            .build();
    final Map<String, String> names = Map.of("spring-boot", "Spring Boot", "kafka", "Apache Kafka");

    private List<PackageIssue> check(Claim claim) {
        return validator.claims("resume", List.of(claim), snapshot, V, names);
    }

    private static Claim claim(String text, String... facts) {
        return new Claim(text, Arrays.stream(facts).map(f -> new FactRef(V, f)).toList());
    }

    @Test
    void restatedFactsPass() {
        assertThat(check(claim("Senior Engineer, Now GmbH · Jan 2020 – Present", "exp"))).isEmpty();
        assertThat(check(claim("Built a Kafka pipeline handling 2M events/day.", "ach", "exp"))).isEmpty();
        assertThat(check(claim("Bachelor's degree, Computer Science · Example U (2013 – 2016)", "edu"))).isEmpty();
        assertThat(check(claim("Spring Boot", "sk"))).isEmpty();
        assertThat(check(claim("Relevant skills: Apache Kafka, Spring Boot.", "exp", "sk"))).isEmpty();
    }

    @Test
    void fabricatedStatementsCitingRealFactsFail() {
        assertThat(check(claim("Built a Kafka pipeline handling 20M events/day.", "ach")))
                .singleElement().satisfies(i -> {
                    assertThat(i.code()).isEqualTo("UNGROUNDED_CLAIM");
                    assertThat(i.message()).contains("20m");
                });
        assertThat(check(claim("Increased revenue by 300% at Now GmbH", "exp"))).extracting(PackageIssue::code).containsExactly("UNGROUNDED_CLAIM");
        assertThat(check(claim("Staff Engineer, Now GmbH · Jan 2020 – Present", "exp"))).extracting(PackageIssue::code).containsExactly("UNGROUNDED_CLAIM");
        assertThat(check(claim("Master's degree, Computer Science · Example U", "edu"))).extracting(PackageIssue::code).containsExactly("UNGROUNDED_CLAIM");
    }

    @Test
    void structuralProblemsAreBlocking() {
        assertThat(check(new Claim("Anything", List.of()))).extracting(PackageIssue::code).containsExactly("CLAIM_WITHOUT_SOURCE");
        assertThat(check(claim("Led 40 engineers.", "unv"))).extracting(PackageIssue::code).containsExactly("UNVERIFIED_FACT");
        assertThat(check(claim("Something", "nope"))).extracting(PackageIssue::code).containsExactly("UNKNOWN_FACT");
        assertThat(check(new Claim("Senior Engineer", List.of(new FactRef(UUID.randomUUID(), "exp"))))).extracting(PackageIssue::code)
                .containsExactly("FOREIGN_FACT");
        assertThat(check(new Claim("x", List.of()))).allSatisfy(i -> assertThat(i.severity()).isEqualTo(PackageIssue.Severity.BLOCKING));
    }

    @Test
    void userApprovedTextIsTheCandidatesOwnStatement() {
        assertThat(check(Claim.userApproved("Passionate about reliable systems."))).isEmpty();
    }

    @Test
    void answersMustRestOnFactsContactOrTheCandidate() {
        ApplicationQuestion q = new ApplicationQuestion("q", "Years with Spring?", ApplicationQuestion.QuestionType.TEXT, true, List.of());
        assertThat(validator.answers(List.of(new Answer(q, new AnswerDraft("q", AnswerDraft.Status.DRAFTED, "4",
                List.of(new FactRef(V, "sk")), AnswerDraft.Origin.FACT, null))), snapshot, V)).isEmpty();
        assertThat(validator.answers(List.of(new Answer(q, new AnswerDraft("q", AnswerDraft.Status.DRAFTED, "4",
                List.of(new FactRef(V, "unv")), AnswerDraft.Origin.FACT, null))), snapshot, V))
                .extracting(PackageIssue::code).containsExactly("UNVERIFIED_FACT");
        assertThat(validator.answers(List.of(new Answer(q, new AnswerDraft("q", AnswerDraft.Status.DRAFTED, "https://evil.example",
                List.of(), AnswerDraft.Origin.CONTACT, null))), snapshot, V)).extracting(PackageIssue::code).containsExactly("UNGROUNDED_ANSWER");
        assertThat(validator.answers(List.of(new Answer(q, new AnswerDraft("q", AnswerDraft.Status.DRAFTED, "https://github.com/jane",
                List.of(), AnswerDraft.Origin.CONTACT, null))), snapshot, V)).isEmpty();
        assertThat(validator.answers(List.of(new Answer(q, AnswerDraft.needsUser("q", "x"))), snapshot, V))
                .singleElement().satisfies(i -> {
                    assertThat(i.code()).isEqualTo("ANSWER_REQUIRED");
                    assertThat(i.severity()).isEqualTo(PackageIssue.Severity.WARNING);
                });
    }
}
