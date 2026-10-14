package com.jobsapply.platform.assist.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.assist.internal.FieldMappingPlanner.Decision;
import com.jobsapply.platform.assist.internal.FieldMappingPlanner.FieldDecision;
import com.jobsapply.platform.preparation.PackageContent;
import com.jobsapply.spi.application.FieldMappingPlan.Attachment;
import com.jobsapply.spi.application.FieldMappingPlan.Confidence;
import com.jobsapply.spi.application.FieldMappingPlan.FieldMapping;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.ApplicationQuestion.Kind;
import com.jobsapply.spi.render.ApplicationQuestion.QuestionType;
import com.jobsapply.spi.render.ResumeDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldMappingPlannerTest {

    static final FactRef FACT = new FactRef(UUID.randomUUID(), "sk-java");
    static final Attachment PDF = new Attachment("doc", "resume.pdf", "application/pdf", new byte[] {1});

    final FieldMappingPlanner planner = new FieldMappingPlanner();

    static PackageContent pkg(String name, String phone, List<String> links, PackageContent.Answer... answers) {
        ResumeDocument resume = new ResumeDocument("one-page", UUID.randomUUID(),
                new ResumeDocument.Header(name, "jane@example.org", phone, "Berlin", links), List.of());
        return new PackageContent(Instant.now(), null, null, "Acme", null, null, null, null, resume, null, List.of(), List.of(answers),
                List.of(), null, List.of());
    }

    static PackageContent.Answer answer(String id, String text, AnswerDraft.Status status, String value, AnswerDraft.Origin origin) {
        return new PackageContent.Answer(new ApplicationQuestion(id, text, QuestionType.TEXTAREA, true, List.of()),
                new AnswerDraft(id, status, value, origin == AnswerDraft.Origin.FACT ? List.of(FACT) : List.of(), origin, null));
    }

    static ApplicationQuestion field(String id, String label, QuestionType type, String... options) {
        return new ApplicationQuestion(id, label, type, false, List.of(options));
    }

    private FieldDecision decide(ApplicationQuestion field, PackageContent content) {
        return planner.plan(List.of(field), content, Optional.of(PDF), Optional.empty()).decisions().getFirst();
    }

    private Optional<FieldMapping> mapping(ApplicationQuestion field, PackageContent content) {
        return planner.plan(List.of(field), content, Optional.of(PDF), Optional.empty()).highConfidence().mappings().stream().findFirst();
    }

    @Test
    void contactDetailsComeFromTheResumeHeader() {
        PackageContent content = pkg("Jane Example", null, List.of("https://www.linkedin.com/in/jane", "https://github.com/jane"));
        assertThat(mapping(field("email", "Email", QuestionType.TEXT), content)).get().extracting(FieldMapping::value).isEqualTo("jane@example.org");
        assertThat(mapping(field("first_name", "First Name", QuestionType.TEXT), content)).get().extracting(FieldMapping::value).isEqualTo("Jane");
        assertThat(mapping(field("x1", "Last name", QuestionType.TEXT), content)).get().extracting(FieldMapping::value).isEqualTo("Example");
        assertThat(mapping(field("q", "LinkedIn Profile", QuestionType.TEXT), content)).get().extracting(FieldMapping::value)
                .isEqualTo("https://www.linkedin.com/in/jane");
        assertThat(decide(field("phone", "Phone", QuestionType.TEXT), content).decision()).isEqualTo(Decision.LEAVE_TO_YOU);
        assertThat(decide(field("preferred_name", "Preferred name", QuestionType.TEXT), content).decision()).isEqualTo(Decision.LEAVE_TO_YOU);
    }

    @Test
    void aNameThatDoesNotSplitCleanlyIsLeftToYou() {
        PackageContent content = pkg("Maria de la Cruz", null, List.of());
        assertThat(decide(field("first_name", "First Name", QuestionType.TEXT), content).note()).contains("does not split");
        assertThat(mapping(field("name", "Full name", QuestionType.TEXT), content)).get().extracting(FieldMapping::value)
                .isEqualTo("Maria de la Cruz");
    }

    @Test
    void reviewedAnswersMatchByIdOrExactWordingAndCarryTheirSources() {
        PackageContent content = pkg("Jane Example", null, List.of(),
                answer("custom-1", "Will you require visa sponsorship?", AnswerDraft.Status.DRAFTED, "no", AnswerDraft.Origin.USER),
                answer("question_7", "Years of experience", AnswerDraft.Status.DRAFTED, "7", AnswerDraft.Origin.FACT));

        FieldMapping sponsorship = mapping(field("question_2", "Will you require visa sponsorship?*", QuestionType.SINGLE_SELECT, "Yes", "No"),
                content).orElseThrow();
        assertThat(sponsorship.value()).as("the form's own option text").isEqualTo("No");
        assertThat(sponsorship.confidence()).isEqualTo(Confidence.HIGH);

        FieldMapping years = mapping(field("question_7", "How long have you worked?", QuestionType.NUMBER), content).orElseThrow();
        assertThat(years.value()).isEqualTo("7");
        assertThat(years.sources()).containsExactly(FACT);
    }

    @Test
    void answersThatDoNotFitTheFieldAreLeftToYou() {
        PackageContent content = pkg("Jane Example", null, List.of(),
                answer("q1", "Notice period", AnswerDraft.Status.DRAFTED, "three months", AnswerDraft.Origin.USER),
                answer("q2", "Salary expectation", AnswerDraft.Status.NEEDS_USER_INPUT, null, AnswerDraft.Origin.NONE));
        assertThat(decide(field("q1", "Notice period", QuestionType.SINGLE_SELECT, "1 month", "2 months"), content).note())
                .contains("not one of the form's options");
        assertThat(decide(field("q1", "Notice period", QuestionType.NUMBER), content).note()).contains("not a number");
        assertThat(decide(field("q1", "Notice period", QuestionType.SINGLE_SELECT), content).note()).contains("from the form's list");
        assertThat(decide(field("q2", "Salary expectation", QuestionType.TEXT), content).note()).contains("no answer");
        assertThat(decide(field("q9", "Anything else?", QuestionType.TEXTAREA), content).decision()).isEqualTo(Decision.LEAVE_TO_YOU);
    }

    @Test
    void selfIdentificationAndConsentAreAlwaysYours() {
        PackageContent content = pkg("Jane Example", null, List.of(),
                answer("gender", "Gender", AnswerDraft.Status.DRAFTED, "Female", AnswerDraft.Origin.USER));
        assertThat(decide(field("gender", "Gender", QuestionType.SINGLE_SELECT, "Female", "Male"), content).note()).contains("self-identification");
        assertThat(decide(new ApplicationQuestion("d1", "Are you a protected veteran?", QuestionType.SINGLE_SELECT, false, List.of("Yes", "No"),
                Kind.SELF_IDENTIFICATION), content).decision()).isEqualTo(Decision.LEAVE_TO_YOU);
        assertThat(decide(field("consent", "I agree to the privacy policy", QuestionType.BOOLEAN), content).note()).contains("consent");
        assertThat(decide(field("lang", "Which languages do you speak?", QuestionType.TEXT), content).note())
                .as("'language' is not 'age'").doesNotContain("self-identification");
    }

    @Test
    void theResumeFieldGetsTheGeneratedPdf() {
        PackageContent content = pkg("Jane Example", null, List.of());
        assertThat(mapping(field("resume", "Resume/CV", QuestionType.FILE), content)).get().extracting(FieldMapping::attachment).isEqualTo(PDF);
        assertThat(planner.plan(List.of(field("resume", "Resume/CV", QuestionType.FILE)), content, Optional.empty(), Optional.empty())
                .decisions().getFirst().decision()).isEqualTo(Decision.LEAVE_TO_YOU);
        assertThat(decide(field("transcript", "Transcript", QuestionType.FILE), content).decision()).isEqualTo(Decision.LEAVE_TO_YOU);
    }
}
