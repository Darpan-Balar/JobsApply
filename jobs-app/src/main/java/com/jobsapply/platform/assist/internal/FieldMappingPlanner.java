package com.jobsapply.platform.assist.internal;

import com.jobsapply.platform.preparation.PackageContent;
import com.jobsapply.spi.application.FieldMappingPlan;
import com.jobsapply.spi.application.FieldMappingPlan.Attachment;
import com.jobsapply.spi.application.FieldMappingPlan.Confidence;
import com.jobsapply.spi.application.FieldMappingPlan.FieldMapping;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.ApplicationQuestion.QuestionType;
import com.jobsapply.spi.render.ResumeDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides what goes into each form field (ADR-0012). Values come only from the package the candidate approved: their
 * reviewed answers, the contact details in the resume header, and the generated resume. Anything uncertain is left to
 * the candidate, and some fields are always theirs: voluntary self-identification, consent and anything matching
 * nothing in the package.
 */
public final class FieldMappingPlanner {

    private static final Pattern SELF_IDENTIFICATION = Pattern.compile(
            "\\b(gender|sex|race|racial|ethnic\\w*|hispanic|latin[oax]|veteran|disabilit\\w*|pronouns?|sexual orientation|transgender"
                    + "|date of birth|birth ?date|age|religio\\w*|marital)\\b");
    private static final Pattern CONSENT = Pattern.compile(
            "\\b(i agree|i consent|i acknowledge|i certify|i confirm|i attest|consent|privacy (policy|notice)|terms( and conditions)?|gdpr)\\b");
    private static final Pattern RESUME = Pattern.compile("\\b(resume|résumé|cv|curriculum vitae)\\b");
    private static final Pattern COVER_LETTER = Pattern.compile("\\bcover (letter|note)\\b");

    public enum Decision {
        FILL,
        LEAVE_TO_YOU
    }

    /** @param note why (shown to the candidate; never contains the value) */
    public record FieldDecision(String fieldId, String label, boolean required, Decision decision, String note) {}

    public record Plan(FieldMappingPlan highConfidence, List<FieldDecision> decisions) {}

    /**
     * @param resumePdf the package's generated resume, if any
     * @param coverNote the package's cover note text, if any
     */
    public Plan plan(List<ApplicationQuestion> fields, PackageContent content, Optional<Attachment> resumePdf, Optional<String> coverNote) {
        List<FieldMapping> mappings = new ArrayList<>();
        List<FieldDecision> decisions = new ArrayList<>();
        for (ApplicationQuestion field : fields) {
            Result result = decide(field, content, resumePdf, coverNote);
            if (result.mapping() != null && result.mapping().confidence() == Confidence.HIGH) {
                mappings.add(result.mapping());
                decisions.add(new FieldDecision(field.id(), field.text(), field.required(), Decision.FILL, result.note()));
            } else {
                decisions.add(new FieldDecision(field.id(), field.text(), field.required(), Decision.LEAVE_TO_YOU, result.note()));
            }
        }
        return new Plan(new FieldMappingPlan(mappings), decisions);
    }

    private record Result(FieldMapping mapping, String note) {
        static Result leave(String note) {
            return new Result(null, note);
        }
    }

    private Result decide(ApplicationQuestion field, PackageContent content, Optional<Attachment> resumePdf, Optional<String> coverNote) {
        String label = normalize(field.text());
        String key = normalize(field.id());
        if (field.kind() == ApplicationQuestion.Kind.SELF_IDENTIFICATION || SELF_IDENTIFICATION.matcher(label).find()) {
            return Result.leave("voluntary self-identification: only you answer this");
        }
        if (field.type() == QuestionType.BOOLEAN && CONSENT.matcher(label).find()) {
            return Result.leave("consent is yours to give");
        }
        Optional<Result> answered = fromAnswers(field, content);
        if (answered.isPresent()) {
            return answered.get();
        }
        if (field.type() == QuestionType.FILE) {
            if (RESUME.matcher(label).find() || RESUME.matcher(key).find()) {
                return resumePdf.map(pdf -> high(field, null, pdf, "your generated resume (PDF)"))
                        .orElseGet(() -> Result.leave("the package has no PDF resume"));
            }
            return Result.leave("no matching document in the package");
        }
        if (COVER_LETTER.matcher(label).find() || COVER_LETTER.matcher(key).find()) {
            if (field.type() == QuestionType.TEXTAREA || field.type() == QuestionType.TEXT) {
                return coverNote.map(text -> high(field, text, null, "your approved cover note"))
                        .orElseGet(() -> Result.leave("the package has no cover note"));
            }
            return Result.leave("no matching document in the package");
        }
        return fromContact(field, label, key, content.resume() == null ? null : content.resume().header());
    }

    /** A reviewed answer for this exact question id, or for a question with the same wording. */
    private Optional<Result> fromAnswers(ApplicationQuestion field, PackageContent content) {
        List<PackageContent.Answer> byId = content.answers().stream().filter(a -> a.question().id().equals(field.id())).toList();
        List<PackageContent.Answer> byText = content.answers().stream()
                .filter(a -> normalize(a.question().text()).equals(normalize(field.text()))).toList();
        List<PackageContent.Answer> match = !byId.isEmpty() ? byId : byText;
        if (match.size() != 1) {
            return match.isEmpty() ? Optional.empty() : Optional.of(Result.leave("several of your answers could fit; choose yourself"));
        }
        PackageContent.Answer answer = match.getFirst();
        if (answer.question().kind() == ApplicationQuestion.Kind.SELF_IDENTIFICATION) {
            return Optional.of(Result.leave("voluntary self-identification: only you answer this"));
        }
        AnswerDraft draft = answer.draft();
        if (draft.status() != AnswerDraft.Status.DRAFTED || draft.answer() == null || draft.answer().isBlank()) {
            return Optional.of(Result.leave("your package has no answer for this question"));
        }
        String origin = switch (draft.origin()) {
            case USER -> "your answer";
            case FACT -> "your verified facts";
            case CONTACT -> "your contact details";
            case NONE -> "your package";
        };
        return Optional.of(choice(field, draft.answer(), origin, draft.sources()));
    }

    private Result fromContact(ApplicationQuestion field, String label, String key, ResumeDocument.Header header) {
        if (header == null) {
            return Result.leave("no approved answer for this field");
        }
        if (is(key, "email") || label.matches("e ?mail( address)?")) {
            return text(field, header.email(), "your e-mail");
        }
        if (is(key, "phone") || label.matches("(phone|mobile|telephone)( number)?")) {
            return text(field, header.phone(), "your phone number");
        }
        boolean first = is(key, "first_name") || label.matches("(first|given) name");
        boolean last = is(key, "last_name") || label.matches("(last|family) name|surname");
        if (first || last) {
            String[] parts = header.name() == null ? new String[0] : header.name().strip().split("\\s+");
            if (parts.length != 2) {
                return Result.leave("your name does not split unambiguously into first and last name");
            }
            return text(field, first ? parts[0] : parts[1], first ? "first part of your name" : "last part of your name");
        }
        if (is(key, "name") || is(key, "full_name") || label.matches("(full )?name")) {
            return text(field, header.name(), "your name");
        }
        if (key.contains("linkedin") || label.contains("linkedin")) {
            return link(field, header, "linkedin.com", "your LinkedIn link");
        }
        if (key.contains("github") || label.contains("github")) {
            return link(field, header, "github.com", "your GitHub link");
        }
        if (is(key, "preferred_name") || label.contains("preferred name")) {
            return Result.leave("how you prefer to be called is your choice");
        }
        return Result.leave("no approved answer for this field");
    }

    private static Result link(ApplicationQuestion field, ResumeDocument.Header header, String host, String origin) {
        List<String> matching = header.links().stream().filter(l -> l.toLowerCase(Locale.ROOT).contains(host)).toList();
        return matching.size() == 1 ? text(field, matching.getFirst(), origin) : Result.leave("no single " + host + " link in your profile");
    }

    private static Result text(ApplicationQuestion field, String value, String origin) {
        if (value == null || value.isBlank()) {
            return Result.leave("missing from your contact details");
        }
        if (field.type() != QuestionType.TEXT && field.type() != QuestionType.TEXTAREA) {
            return Result.leave("not a text field");
        }
        return high(field, value.strip(), null, origin);
    }

    /** Text goes into text fields; for choices, the answer must be one of the form's options (case-insensitive). */
    private static Result choice(ApplicationQuestion field, String answer, String origin, List<FactRef> sources) {
        return switch (field.type()) {
            case TEXT, TEXTAREA, NUMBER -> field.type() == QuestionType.NUMBER && !answer.strip().matches("-?\\d+(\\.\\d+)?")
                    ? Result.leave("your answer is not a number")
                    : high(field, answer.strip(), null, origin, sources);
            case SINGLE_SELECT, BOOLEAN -> {
                List<String> options = field.type() == QuestionType.BOOLEAN ? List.of("Yes", "No") : field.options();
                Optional<String> option = options.stream().filter(o -> o.equalsIgnoreCase(answer.strip())).findFirst();
                if (field.type() == QuestionType.SINGLE_SELECT && options.isEmpty()) {
                    yield Result.leave("pick this from the form's list yourself");
                }
                yield option.map(o -> high(field, o, null, origin, sources))
                        .orElseGet(() -> Result.leave("your answer is not one of the form's options"));
            }
            case MULTI_SELECT, DATE, FILE -> Result.leave("this kind of field is left to you");
        };
    }

    private static Result high(ApplicationQuestion field, String value, Attachment attachment, String origin) {
        return high(field, value, attachment, origin, List.of());
    }

    private static Result high(ApplicationQuestion field, String value, Attachment attachment, String origin, List<FactRef> sources) {
        return new Result(new FieldMapping(field.id(), value, attachment, Confidence.HIGH, sources, origin), origin);
    }

    private static boolean is(String key, String expected) {
        return key.equals(normalize(expected));
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
