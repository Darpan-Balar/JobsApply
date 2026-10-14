package com.jobsapply.adapters.preparation;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.profile.RoleProfileDefinition.ScreeningQuestionRule;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.AnswerDrafter;
import com.jobsapply.spi.render.ApplicationQuestion;
import java.math.BigDecimal;
import java.net.URI;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code rule-based-answers}. Order of precedence:
 *
 * <ol>
 *   <li>self-identification questions → never answered;
 *   <li>the role profile's screening rules (first regex match): ALWAYS_ASK_USER / SKIP / FROM_FACT;
 *   <li>built-in sensitive topics (sponsorship, salary, notice period, referrals, relocation, disability, criminal
 *       history, ...) → never answered;
 *   <li>profile links (LinkedIn, GitHub, portfolio) → from the candidate's contact details;
 *   <li>anything else → the candidate answers. Free-text "why us" answers are never written for the candidate.
 * </ol>
 * A drafted answer for a select question must be one of its options, otherwise the candidate answers.
 */
public final class RuleBasedAnswerDrafter implements AnswerDrafter {

    public static final String ID = "rule-based-answers";

    private static final Pattern SENSITIVE = Pattern.compile("(?i)sponsor|visa|work (permit|authori[sz]ation)|salary|compensation|"
            + "pay range|expected pay|notice period|start date|referr|relocat|disab|veteran|gender|race|ethnic|pronoun|criminal|"
            + "conviction|background check|citizenship|nationality|date of birth|age\\b");
    private static final Pattern LINK = Pattern.compile("(?i)linkedin|github|portfolio|website|personal (site|page)");

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.ANSWER_DRAFTER)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "never guesses; sensitive questions always go to the candidate")).build();
    }

    @Override
    public AnswerDraft draft(ApplicationQuestion question, MatchContext context) {
        if (question.kind() == ApplicationQuestion.Kind.SELF_IDENTIFICATION) {
            return AnswerDraft.needsUser(question.id(), "voluntary self-identification is never answered automatically");
        }
        for (ScreeningQuestionRule rule : context.role().screeningQuestions()) {
            if (Pattern.compile(rule.match()).matcher(question.text()).find()) {
                return byRule(question, rule, context);
            }
        }
        if (SENSITIVE.matcher(question.text()).find()) {
            return AnswerDraft.needsUser(question.id(), "sensitive topic: only you can answer this");
        }
        if (LINK.matcher(question.text()).find()) {
            Optional<String> link = contactLink(question.text(), context);
            if (link.isPresent()) {
                return options(question, new AnswerDraft(question.id(), AnswerDraft.Status.DRAFTED, link.get(), List.of(),
                        AnswerDraft.Origin.CONTACT, "from your contact details"));
            }
        }
        return AnswerDraft.needsUser(question.id(), "no rule or verified fact answers this question");
    }

    private AnswerDraft byRule(ApplicationQuestion question, ScreeningQuestionRule rule, MatchContext context) {
        return switch (rule.policy()) {
            case ALWAYS_ASK_USER -> AnswerDraft.needsUser(question.id(), "your role profile says to always ask you");
            case SKIP -> question.required()
                    ? AnswerDraft.needsUser(question.id(), "required, although your role profile says to skip it")
                    : new AnswerDraft(question.id(), AnswerDraft.Status.NOT_APPLICABLE, null, List.of(), AnswerDraft.Origin.NONE,
                            "skipped by your role profile");
            case FROM_FACT -> fromFact(rule.fact(), context)
                    .map(answer -> options(question, new AnswerDraft(question.id(), AnswerDraft.Status.DRAFTED, answer.value(),
                            answer.sources(), AnswerDraft.Origin.FACT, "from verified fact(s)")))
                    .orElseGet(() -> AnswerDraft.needsUser(question.id(), "no verified fact '" + rule.fact() + "'"));
        };
    }

    private record FactAnswer(String value, List<FactRef> sources) {}

    /** {@code experience.total-years}, {@code skill.<key>.years}, or a candidate attribute key. */
    static Optional<FactAnswer> fromFact(String fact, MatchContext context) {
        var c = context.candidate();
        var version = context.candidateProfileVersionId();
        if ("experience.total-years".equals(fact)) {
            return CandidateFacts.totalExperienceYears(c, YearMonth.from(context.evaluatedAt().atZone(ZoneOffset.UTC)))
                    .map(years -> new FactAnswer(years.stripTrailingZeros().toPlainString(),
                            CandidateFacts.experiences(c).map(e -> new FactRef(version, e.id())).toList()));
        }
        if (fact.startsWith("skill.") && fact.endsWith(".years")) {
            String skill = fact.substring("skill.".length(), fact.length() - ".years".length());
            return c.skills().stream().filter(s -> s.verification().canBackClaims() && s.skill().equals(skill) && s.years() != null)
                    .findFirst()
                    .map(s -> new FactAnswer(years(s), List.of(new FactRef(version, s.id()))));
        }
        return CandidateFacts.attribute(c, fact).map(a -> new FactAnswer(a.value(), List.of(new FactRef(version, a.id()))));
    }

    private static String years(CandidateSkill s) {
        BigDecimal years = s.years();
        return years.stripTrailingZeros().toPlainString();
    }

    private static Optional<String> contactLink(String text, MatchContext context) {
        if (context.candidate().contact() == null) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String wanted = lower.contains("linkedin") ? "linkedin." : lower.contains("github") ? "github." : null;
        return context.candidate().contact().links().stream().map(URI::toString)
                .filter(link -> wanted == null || link.toLowerCase(Locale.ROOT).contains(wanted))
                .findFirst();
    }

    /** A select question only accepts one of its options. */
    private static AnswerDraft options(ApplicationQuestion question, AnswerDraft draft) {
        if (question.options().isEmpty() || draft.status() != AnswerDraft.Status.DRAFTED) {
            return draft;
        }
        return question.options().stream().filter(o -> o.equalsIgnoreCase(draft.answer())).findFirst()
                .map(o -> new AnswerDraft(draft.questionId(), draft.status(), o, draft.sources(), draft.origin(), draft.note()))
                .orElseGet(() -> AnswerDraft.needsUser(question.id(), "the verified value is not one of the offered options"));
    }
}
