package com.jobsapply.platform.preparation;

import com.jobsapply.platform.preparation.PackageContent.Answer;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue.Severity;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateFact;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.Claim;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Proves that generated content restates the candidate's facts and nothing else.
 *
 * <ol>
 *   <li><b>Structural</b>: every claim cites at least one fact; every cited fact exists in <em>this</em> candidate
 *       version and may back claims (verified, preference or derived).
 *   <li><b>Grounding</b> (document claims): every word and number of a claim (apart from a small fixed set of formatting words) appears
 *       in the facts it cites. A real fact cited for an invented statement ("increased revenue 300%") fails.
 * </ol>
 * Content the candidate wrote and approved ({@code userApproved}) is exempt — it is their own statement.
 * Issue messages contain fact ids and offending words only, never other personal data.
 */
@Component
public class TraceabilityValidator {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}+#]+");
    /** Formatting words the built-in generators use around fact values. */
    private static final Set<String> GLUE = Set.of(
            "a", "an", "and", "or", "the", "of", "in", "at", "for", "with", "to", "on", "by", "as", "from", "s",
            "present", "currently", "relevant", "skills", "degree", "progress", "school",
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

    public List<PackageIssue> claims(String where, List<Claim> claims, CandidateSnapshot snapshot, UUID versionId,
            Map<String, String> skillNames) {
        return check(where, claims, snapshot, versionId, skillNames, true);
    }

    private List<PackageIssue> check(String where, List<Claim> claims, CandidateSnapshot snapshot, UUID versionId,
            Map<String, String> skillNames, boolean grounding) {
        List<PackageIssue> issues = new ArrayList<>();
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            String at = where + "[" + i + "]";
            if (claim.userApproved()) {
                continue;
            }
            if (claim.sources().isEmpty()) {
                issues.add(blocking("CLAIM_WITHOUT_SOURCE", at + ": a statement cites no fact"));
                continue;
            }
            List<CandidateFact> facts = new ArrayList<>();
            for (FactRef ref : claim.sources()) {
                if (!versionId.equals(ref.candidateProfileVersionId())) {
                    issues.add(blocking("FOREIGN_FACT", at + ": cites fact '" + ref.factId() + "' of another profile version"));
                    continue;
                }
                Optional<CandidateFact> fact = snapshot.fact(ref.factId());
                if (fact.isEmpty()) {
                    issues.add(blocking("UNKNOWN_FACT", at + ": cites unknown fact '" + ref.factId() + "'"));
                } else if (!fact.get().verification().canBackClaims()) {
                    issues.add(blocking("UNVERIFIED_FACT", at + ": cites fact '" + ref.factId() + "' which is "
                            + fact.get().verification() + "; confirm it first"));
                } else {
                    facts.add(fact.get());
                }
            }
            if (grounding && facts.size() == claim.sources().size()) {
                Set<String> ungrounded = ungrounded(claim.text(), facts, skillNames);
                if (!ungrounded.isEmpty()) {
                    issues.add(blocking("UNGROUNDED_CLAIM", at + ": words not found in the cited facts: " + String.join(", ", ungrounded)));
                }
            }
        }
        return issues;
    }

    /** Drafted answers must rest on claimable facts (FACT), match the candidate's own contact details (CONTACT), or be theirs (USER). */
    public List<PackageIssue> answers(List<Answer> answers, CandidateSnapshot snapshot, UUID versionId) {
        List<PackageIssue> issues = new ArrayList<>();
        for (Answer answer : answers) {
            AnswerDraft draft = answer.draft();
            String at = "answer '" + answer.question().id() + "'";
            if (draft.status() == AnswerDraft.Status.NEEDS_USER_INPUT) {
                issues.add(new PackageIssue(Severity.WARNING,
                        answer.question().required() ? "ANSWER_REQUIRED" : "ANSWER_OPTIONAL",
                        at + (answer.question().required() ? " is required and needs your answer" : " is optional and unanswered")));
                continue;
            }
            if (draft.status() != AnswerDraft.Status.DRAFTED) {
                continue;
            }
            switch (draft.origin()) {
                case USER -> { }
                case CONTACT -> {
                    if (!contactValues(snapshot.contact()).contains(draft.answer())) {
                        issues.add(blocking("UNGROUNDED_ANSWER", at + " is not one of your contact details"));
                    }
                }
                case FACT -> {
                    // Answers may be derived from facts (e.g. total years), so only their sources are checked here.
                    List<PackageIssue> sourceIssues = check(at, List.of(new Claim(draft.answer(), draft.sources())), snapshot, versionId, Map.of(), false);
                    sourceIssues.stream().map(i -> new PackageIssue(i.severity(), i.code(), i.message().replace(at + "[0]", at)))
                            .forEach(issues::add);
                }
                case NONE -> issues.add(blocking("UNGROUNDED_ANSWER", at + " has an answer without an origin"));
            }
        }
        return issues;
    }

    Set<String> ungrounded(String text, List<CandidateFact> facts, Map<String, String> skillNames) {
        Set<String> available = new HashSet<>();
        for (CandidateFact fact : facts) {
            tokens(factText(fact, skillNames)).forEach(available::add);
        }
        return tokens(text).stream().filter(t -> !GLUE.contains(t) && !available.contains(t))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static List<String> tokens(String text) {
        return Arrays.stream(TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))).filter(t -> !t.isBlank()).toList();
    }

    private static String factText(CandidateFact fact, Map<String, String> skillNames) {
        StringBuilder text = new StringBuilder();
        switch (fact) {
            case CandidateExperience e -> text.append(e.title()).append(' ').append(e.employer()).append(' ')
                    .append(nullToEmpty(e.location())).append(' ').append(e.start()).append(' ').append(e.end() == null ? "" : e.end())
                    .append(' ').append(skills(e.skills(), skillNames));
            case CandidateAchievement a -> text.append(a.text()).append(' ').append(skills(a.skills(), skillNames));
            case CandidateEducation e -> text.append(e.institution()).append(' ').append(e.level().name().replace('_', ' '))
                    .append(' ').append(nullToEmpty(e.field())).append(' ').append(e.start() == null ? "" : e.start())
                    .append(' ').append(e.end() == null ? "" : e.end());
            case CandidateSkill s -> text.append(skills(List.of(s.skill()), skillNames)).append(' ')
                    .append(s.years() == null ? "" : s.years().stripTrailingZeros().toPlainString()).append(' ')
                    .append(s.proficiency() == null ? "" : s.proficiency().name());
            case CandidateAttribute a -> text.append(a.key()).append(' ').append(a.value());
        }
        return text.toString();
    }

    private static String skills(List<String> keys, Map<String, String> skillNames) {
        return keys.stream().map(k -> k + " " + skillNames.getOrDefault(k, "")).collect(Collectors.joining(" "));
    }

    private static Set<String> contactValues(CandidateContact contact) {
        Set<String> values = new HashSet<>();
        if (contact != null) {
            for (String v : new String[] {contact.fullName(), contact.email(), contact.phone()}) {
                if (v != null) {
                    values.add(v);
                }
            }
            contact.links().stream().map(URI::toString).forEach(values::add);
        }
        return values;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static PackageIssue blocking(String code, String message) {
        return new PackageIssue(Severity.BLOCKING, code, message);
    }
}
