package com.jobsapply.adapters.preparation;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.CoverNoteGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code fact-based}: up to three verified achievements most relevant to the job (verbatim), the current position,
 * and the candidate's skills the job mentions. The platform wraps them in a fixed greeting and closing.
 */
public final class FactBasedCoverNoteGenerator implements CoverNoteGenerator {

    public static final String ID = "fact-based";

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.CONTENT_GENERATOR)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "deterministic; restates verified facts only")).build();
    }

    @Override
    public String strategy() {
        return ID;
    }

    @Override
    public List<Claim> claims(MatchContext context, Map<String, String> skillNames) {
        UUID version = context.candidateProfileVersionId();
        List<Claim> claims = new ArrayList<>();
        CandidateFacts.experiences(context.candidate()).filter(e -> e.end() == null)
                .max(Comparator.comparing(e -> e.start()))
                .ifPresent(e -> claims.add(new Claim("Currently " + e.title() + " at " + e.employer() + ".",
                        List.of(new FactRef(version, e.id())))));
        context.candidate().achievements().stream().filter(a -> a.verification().canBackClaims())
                .sorted(FactBasedResumeGenerator.byRelevance(context.job().skills())).limit(3)
                .forEach(a -> claims.add(new Claim(a.text(), List.of(new FactRef(version, a.id())))));
        List<String> relevant = CandidateFacts.skills(context.candidate()).stream()
                .filter(context.job().skills()::contains)
                .sorted(Comparator.comparing((String s) -> skillNames.getOrDefault(s, s))).toList();
        if (!relevant.isEmpty()) {
            List<FactRef> refs = new ArrayList<>();
            relevant.forEach(s -> FactBasedResumeGenerator.skillEvidence(context.candidate(), version, s).ifPresent(refs::addAll));
            claims.add(new Claim("Relevant skills: " + String.join(", ", relevant.stream().map(s -> skillNames.getOrDefault(s, s)).toList()) + ".",
                    refs.stream().distinct().toList()));
        }
        return claims;
    }
}
