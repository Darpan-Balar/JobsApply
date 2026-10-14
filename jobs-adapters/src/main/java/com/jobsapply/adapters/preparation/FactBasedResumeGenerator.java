package com.jobsapply.adapters.preparation;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.ResumeContentGenerator;
import com.jobsapply.spi.render.ResumeDocument;
import com.jobsapply.spi.render.ResumeTemplateDefinition;
import java.net.URI;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code fact-based-resume}: tailors a resume to a job by selection and ordering only.
 *
 * <ul>
 *   <li>Experience: most recent first (up to {@code maxExperiences}); each position cites its fact; its achievements
 *       are restated verbatim, most relevant to the job's skills first (up to {@code maxAchievementsPerExperience}).
 *   <li>Skills: the job's skills that the candidate has first, then the rest (up to {@code maxSkills}); each skill cites
 *       the fact(s) asserting it.
 *   <li>Education: highest/most recent first.
 * </ul>
 * Only verified, preference or derived facts are used ({@link CandidateFacts}).
 */
public final class FactBasedResumeGenerator implements ResumeContentGenerator {

    public static final String ID = "fact-based-resume";

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.CONTENT_GENERATOR)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "deterministic; selects and restates verified facts only")).build();
    }

    @Override
    public ResumeDocument generate(MatchContext context, ResumeTemplateDefinition template, Map<String, String> skillNames) {
        CandidateSnapshot candidate = context.candidate();
        UUID version = context.candidateProfileVersionId();
        Set<String> jobSkills = context.job().skills();
        int maxExperiences = intParam(template, "maxExperiences", 6);
        int maxAchievements = intParam(template, "maxAchievementsPerExperience", 3);
        int maxSkills = intParam(template, "maxSkills", 20);

        List<CandidateAchievement> achievements = candidate.achievements().stream()
                .filter(a -> a.verification().canBackClaims()).toList();
        List<ResumeDocument.Section> sections = new ArrayList<>();

        List<Claim> experience = new ArrayList<>();
        CandidateFacts.experiences(candidate)
                .sorted(Comparator.comparing((CandidateExperience e) -> e.end() == null ? YearMonth.of(9999, 12) : e.end())
                        .thenComparing(CandidateExperience::start).reversed())
                .limit(maxExperiences)
                .forEach(e -> {
                    experience.add(new Claim(FactText.experience(e), List.of(ref(version, e.id()))));
                    achievements.stream().filter(a -> e.id().equals(a.experienceId()))
                            .sorted(byRelevance(jobSkills)).limit(maxAchievements)
                            .forEach(a -> experience.add(new Claim(a.text(), List.of(ref(version, a.id()), ref(version, e.id())))));
                });
        if (!experience.isEmpty()) {
            sections.add(new ResumeDocument.Section("Experience", experience));
        }

        List<Claim> standalone = achievements.stream().filter(a -> a.experienceId() == null)
                .sorted(byRelevance(jobSkills)).limit(maxAchievements)
                .map(a -> new Claim(a.text(), List.of(ref(version, a.id())))).toList();
        if (!standalone.isEmpty()) {
            sections.add(new ResumeDocument.Section("Selected achievements", standalone));
        }

        List<Claim> skills = CandidateFacts.skills(candidate).stream()
                .sorted(Comparator.comparing((String s) -> !jobSkills.contains(s)).thenComparing(s -> skillNames.getOrDefault(s, s)))
                .limit(maxSkills)
                .map(s -> skillEvidence(candidate, version, s).map(refs -> new Claim(skillNames.getOrDefault(s, s), refs)))
                .flatMap(Optional::stream)
                .toList();
        if (!skills.isEmpty()) {
            sections.add(new ResumeDocument.Section("Skills", skills));
        }

        if (!Boolean.FALSE.equals(template.layout().get("includeEducation"))) {
            List<Claim> education = CandidateFacts.education(candidate)
                    .sorted(Comparator.comparing(CandidateEducation::level).reversed())
                    .map(e -> new Claim(FactText.education(e), List.of(ref(version, e.id())))).toList();
            if (!education.isEmpty()) {
                sections.add(new ResumeDocument.Section("Education", education));
            }
        }
        return new ResumeDocument(template.key(), version, header(candidate.contact()), sections);
    }

    /** The facts asserting a skill: the skill fact itself, else the positions/achievements that list it. */
    static Optional<List<FactRef>> skillEvidence(CandidateSnapshot c, UUID version, String skill) {
        List<FactRef> refs = new ArrayList<>();
        c.skills().stream().filter(f -> f.verification().canBackClaims() && f.skill().equals(skill))
                .map(CandidateSkill::id).forEach(id -> refs.add(ref(version, id)));
        if (refs.isEmpty()) {
            CandidateFacts.experiences(c).filter(e -> e.skills().contains(skill)).map(CandidateExperience::id)
                    .forEach(id -> refs.add(ref(version, id)));
            c.achievements().stream().filter(a -> a.verification().canBackClaims() && a.skills().contains(skill))
                    .map(CandidateAchievement::id).forEach(id -> refs.add(ref(version, id)));
        }
        return refs.isEmpty() ? Optional.empty() : Optional.of(refs);
    }

    static Comparator<CandidateAchievement> byRelevance(Set<String> jobSkills) {
        return Comparator.comparingLong((CandidateAchievement a) -> a.skills().stream().filter(jobSkills::contains).count()).reversed();
    }

    static ResumeDocument.Header header(CandidateContact contact) {
        if (contact == null) {
            return new ResumeDocument.Header(null, null, null, null, List.of());
        }
        String location = contact.city() == null ? contact.countryCode()
                : contact.countryCode() == null ? contact.city() : contact.city() + ", " + contact.countryCode();
        return new ResumeDocument.Header(contact.fullName(), contact.email(), contact.phone(), location,
                contact.links().stream().map(URI::toString).collect(Collectors.toList()));
    }

    static FactRef ref(UUID version, String factId) {
        return new FactRef(version, factId);
    }

    static int intParam(ResumeTemplateDefinition template, String key, int fallback) {
        Object value = template.layout().get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
