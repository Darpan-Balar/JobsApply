package com.jobsapply.adapters.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.render.HtmlResumeRenderer;
import com.jobsapply.adapters.render.PdfResumeRenderer;
import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeDocument;
import com.jobsapply.spi.render.ResumeTemplateDefinition;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PreparationPluginsTest {

    static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    static final Map<String, String> NAMES = Map.of("java", "Java", "kafka", "Apache Kafka", "go", "Go", "spring-boot", "Spring Boot");

    static CandidateSnapshot candidate() {
        return CandidateSnapshot.builder()
                .contact(CandidateContact.builder().fullName("Jane Example").email("jane@example.com")
                        .links(List.of(URI.create("https://www.linkedin.com/in/jane-example"))).build())
                .experiences(List.of(
                        CandidateExperience.builder().id("exp-old").verification(VerificationStatus.VERIFIED).employer("Old Co")
                                .title("Engineer").start(YearMonth.of(2017, 1)).end(YearMonth.of(2019, 12)).skills(List.of("go")).build(),
                        CandidateExperience.builder().id("exp-now").verification(VerificationStatus.VERIFIED).employer("Now GmbH")
                                .title("Senior Engineer").start(YearMonth.of(2020, 1)).skills(List.of("java", "kafka")).build(),
                        CandidateExperience.builder().id("exp-unverified").verification(VerificationStatus.UNVERIFIED).employer("Ghost Inc")
                                .title("CTO").start(YearMonth.of(2015, 1)).end(YearMonth.of(2016, 1)).build()))
                .achievements(List.of(
                        CandidateAchievement.builder().id("ach-kafka").verification(VerificationStatus.VERIFIED)
                                .text("Built a Kafka pipeline handling 2M events/day.").experienceId("exp-now").skills(List.of("kafka")).build(),
                        CandidateAchievement.builder().id("ach-ui").verification(VerificationStatus.VERIFIED)
                                .text("Redesigned the <script>alert(1)</script> admin UI.").experienceId("exp-now").build(),
                        CandidateAchievement.builder().id("ach-unverified").verification(VerificationStatus.UNVERIFIED)
                                .text("Won every award.").experienceId("exp-now").build()))
                .education(List.of(CandidateEducation.builder().id("edu").verification(VerificationStatus.VERIFIED).institution("Example U")
                        .level(DegreeLevel.BACHELOR).field("Computer Science").start(YearMonth.of(2013, 9)).end(YearMonth.of(2016, 6)).completed(true).build()))
                .skills(List.of(CandidateSkill.builder().id("sk-java").verification(VerificationStatus.VERIFIED).skill("java").years(new BigDecimal("6.0")).build()))
                .attributes(List.of(CandidateAttribute.builder().id("notice").verification(VerificationStatus.VERIFIED).key("notice-period-days").value("30").build()))
                .build();
    }

    static MatchContext context(RoleProfileDefinition role) {
        JobSnapshot job = JobSnapshot.builder().postingId(UUID.randomUUID()).title("Backend Engineer").skills(Set.of("kafka", "java")).build();
        return new MatchContext(job, VERSION, candidate(), role, Instant.parse("2026-10-14T10:00:00Z"));
    }

    static RoleProfileDefinition role(List<RoleProfileDefinition.ScreeningQuestionRule> rules) {
        return RoleProfileDefinition.builder().key("r").displayName("R").jobFamily("f")
                .titles(RoleProfileDefinition.TitleRules.builder().include(List.of("engineer")).build())
                .skills(RoleProfileDefinition.SkillRequirements.builder().build())
                .scoring(RoleProfileDefinition.ScoringConfig.builder().strategy("weighted").strategyVersion(1)
                        .weights(Map.of("title", BigDecimal.ONE)).thresholds(new RoleProfileDefinition.Thresholds(BigDecimal.ZERO, BigDecimal.TEN)).build())
                .screeningQuestions(rules).build();
    }

    final ResumeTemplateDefinition template = new ResumeTemplateDefinition("one-page", "html-resume", Map.of("maxAchievementsPerExperience", 2));

    private ResumeDocument resume() {
        return new FactBasedResumeGenerator().generate(context(role(List.of())), template, NAMES);
    }

    private static List<String> texts(ResumeDocument doc, String section) {
        return doc.sections().stream().filter(s -> s.heading().equals(section)).flatMap(s -> s.claims().stream()).map(Claim::text).toList();
    }

    @Test
    void resumeSelectsVerifiedFactsMostRecentAndMostRelevantFirst() {
        ResumeDocument doc = resume();
        assertThat(texts(doc, "Experience")).containsExactly(
                "Senior Engineer, Now GmbH · Jan 2020 – Present",
                "Built a Kafka pipeline handling 2M events/day.",
                "Redesigned the <script>alert(1)</script> admin UI.",
                "Engineer, Old Co · Jan 2017 – Dec 2019");
        assertThat(texts(doc, "Skills")).as("job skills first").containsExactly("Apache Kafka", "Java", "Go");
        assertThat(texts(doc, "Education")).containsExactly("Bachelor's degree, Computer Science · Example U (2013 – 2016)");
        assertThat(doc.sections().stream().flatMap(s -> s.claims().stream()).map(Claim::text))
                .as("unverified facts never appear").noneMatch(t -> t.contains("Ghost") || t.contains("award"));
        assertThat(doc.header().name()).isEqualTo("Jane Example");
    }

    @Test
    void everyGeneratedClaimCitesFactsOfThisVersion() {
        assertThat(resume().sections()).flatMap(ResumeDocument.Section::claims).allSatisfy(claim -> {
            assertThat(claim.userApproved()).isFalse();
            assertThat(claim.sources()).isNotEmpty().allSatisfy(ref -> assertThat(ref.candidateProfileVersionId()).isEqualTo(VERSION));
        });
        Claim kafka = resume().sections().stream().filter(s -> s.heading().equals("Skills")).findFirst().orElseThrow().claims().getFirst();
        assertThat(kafka.sources()).extracting(FactRef::factId).as("a skill without its own fact cites where it is stated")
                .containsExactly("exp-now", "ach-kafka");
    }

    @Test
    void htmlEscapesAllContentAndPdfRenders() {
        RenderedDocument html = ((PluginResult.Ok<RenderedDocument>) new HtmlResumeRenderer().render(resume(), new RenderOptions("text/html", null))).value();
        String page = new String(html.content(), StandardCharsets.UTF_8);
        assertThat(page).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>");

        RenderedDocument pdf = ((PluginResult.Ok<RenderedDocument>) new PdfResumeRenderer().render(resume(), new RenderOptions("application/pdf", null))).value();
        assertThat(new String(pdf.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.content().length).isGreaterThan(1000);
    }

    @Test
    void coverNoteUsesOnlyVerifiedFacts() {
        List<Claim> claims = new FactBasedCoverNoteGenerator().claims(context(role(List.of())), NAMES);
        assertThat(claims).extracting(Claim::text).startsWith("Currently Senior Engineer at Now GmbH.")
                .contains("Built a Kafka pipeline handling 2M events/day.", "Relevant skills: Apache Kafka, Java.")
                .doesNotContain("Won every award.");
    }

    private AnswerDraft answer(ApplicationQuestion q, List<RoleProfileDefinition.ScreeningQuestionRule> rules) {
        return new RuleBasedAnswerDrafter().draft(q, context(role(rules)));
    }

    private static ApplicationQuestion q(String text) {
        return new ApplicationQuestion("q", text, ApplicationQuestion.QuestionType.TEXT, true, List.of());
    }

    @Test
    void answersComeOnlyFromRulesFactsOrContact() {
        var fromFact = List.of(new RoleProfileDefinition.ScreeningQuestionRule("(?i)years.*java", RoleProfileDefinition.ScreeningPolicy.FROM_FACT, "skill.java.years"),
                new RoleProfileDefinition.ScreeningQuestionRule("(?i)notice", RoleProfileDefinition.ScreeningPolicy.FROM_FACT, "notice-period-days"),
                new RoleProfileDefinition.ScreeningQuestionRule("(?i)visa", RoleProfileDefinition.ScreeningPolicy.ALWAYS_ASK_USER, null));

        AnswerDraft years = answer(q("How many years of Java experience?"), fromFact);
        assertThat(years.status()).isEqualTo(AnswerDraft.Status.DRAFTED);
        assertThat(years.answer()).isEqualTo("6");
        assertThat(years.origin()).isEqualTo(AnswerDraft.Origin.FACT);
        assertThat(years.sources()).extracting(FactRef::factId).containsExactly("sk-java");

        assertThat(answer(q("What is your notice period?"), fromFact).answer()).as("an explicit rule wins over the built-in list").isEqualTo("30");
        assertThat(answer(q("Do you need a visa?"), fromFact).status()).isEqualTo(AnswerDraft.Status.NEEDS_USER_INPUT);
        assertThat(answer(q("What are your salary expectations?"), List.of()).note()).contains("sensitive");
        assertThat(answer(q("Will you require sponsorship?"), List.of()).status()).isEqualTo(AnswerDraft.Status.NEEDS_USER_INPUT);
        assertThat(answer(q("In 2-3 sentences, what excites you about this role?"), List.of()).status())
                .as("no prose is ever written for the candidate").isEqualTo(AnswerDraft.Status.NEEDS_USER_INPUT);

        AnswerDraft linkedin = answer(q("LinkedIn Profile"), List.of());
        assertThat(linkedin.answer()).isEqualTo("https://www.linkedin.com/in/jane-example");
        assertThat(linkedin.origin()).isEqualTo(AnswerDraft.Origin.CONTACT);
    }

    @Test
    void selfIdentificationAndInvalidOptionsGoToTheCandidate() {
        ApplicationQuestion demographic = new ApplicationQuestion("d1", "Gender", ApplicationQuestion.QuestionType.SINGLE_SELECT, false,
                List.of("Female", "Male", "Decline"), ApplicationQuestion.Kind.SELF_IDENTIFICATION);
        assertThat(answer(demographic, List.of()).status()).isEqualTo(AnswerDraft.Status.NEEDS_USER_INPUT);

        ApplicationQuestion select = new ApplicationQuestion("s", "Years of Java experience", ApplicationQuestion.QuestionType.SINGLE_SELECT,
                true, List.of("0-2", "3-5", "6+"));
        var rule = List.of(new RoleProfileDefinition.ScreeningQuestionRule("(?i)years", RoleProfileDefinition.ScreeningPolicy.FROM_FACT, "skill.java.years"));
        assertThat(answer(select, rule).note()).contains("not one of the offered options");

        var skip = List.of(new RoleProfileDefinition.ScreeningQuestionRule("(?i)hobby", RoleProfileDefinition.ScreeningPolicy.SKIP, null));
        ApplicationQuestion optional = new ApplicationQuestion("h", "Hobby?", ApplicationQuestion.QuestionType.TEXT, false, List.of());
        assertThat(answer(optional, skip).status()).isEqualTo(AnswerDraft.Status.NOT_APPLICABLE);
        assertThat(answer(q("Hobby?"), skip).status()).as("required questions are never skipped").isEqualTo(AnswerDraft.Status.NEEDS_USER_INPUT);
    }
}
