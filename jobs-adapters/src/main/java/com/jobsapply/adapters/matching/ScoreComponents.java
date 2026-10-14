package com.jobsapply.adapters.matching;

import static com.jobsapply.adapters.matching.MatchingSupport.noEvidence;
import static com.jobsapply.adapters.matching.MatchingSupport.ratio;

import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.matching.ComponentScore;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.matching.ScoreComponent;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The built-in soft score components. A component without evidence returns a {@code null} value (not scored). */
public final class ScoreComponents {

    private ScoreComponents() {}

    static final class Component implements ScoreComponent {
        private final String key;
        private final Function<MatchContext, ComponentScore> logic;

        Component(String key, Function<MatchContext, ComponentScore> logic) {
            this.key = key;
            this.logic = logic;
        }

        @Override
        public PluginDescriptor descriptor() {
            return MatchingSupport.descriptor(key, PluginKind.SCORE_COMPONENT);
        }

        @Override
        public String componentKey() {
            return key;
        }

        @Override
        public ComponentScore score(MatchContext context, Map<String, Object> params) {
            return logic.apply(context);
        }
    }

    public static List<ScoreComponent> all() {
        return List.of(
                new Component("title", ScoreComponents::title),
                new Component("required-skills", ScoreComponents::requiredSkills),
                new Component("preferred-skills", ScoreComponents::preferredSkills),
                new Component("experience", ScoreComponents::experience),
                new Component("location", ScoreComponents::location),
                new Component("seniority", ScoreComponents::seniority),
                new Component("compensation", ScoreComponents::compensation));
    }

    static ComponentScore title(MatchContext c) {
        String title = MatchingSupport.normalize(c.job().title());
        return MatchingSupport.firstPhrase(title, c.role().titles().include())
                .map(p -> new ComponentScore("title", BigDecimal.ONE.setScale(4), List.of("title: " + p), List.of(), List.of()))
                .orElseGet(() -> new ComponentScore("title", BigDecimal.ZERO.setScale(4), List.of(), List.of(),
                        List.of("title matches none of the role's titles")));
    }

    /**
     * Each required skill and each "any of" group is one requirement. A requirement is met when the job mentions it and
     * the candidate has it (verified); "missing" lists requirements the job mentions but the candidate lacks.
     */
    static ComponentScore requiredSkills(MatchContext c) {
        List<List<String>> requirements = new ArrayList<>();
        c.role().skills().required().forEach(s -> requirements.add(List.of(s)));
        requirements.addAll(c.role().skills().requiredAnyOf());
        if (requirements.isEmpty()) {
            return noEvidence("required-skills", "the role profile has no required skills");
        }
        Set<String> job = c.job().skills();
        Set<String> candidate = CandidateFacts.skills(c.candidate());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (List<String> group : requirements) {
            String label = String.join(" | ", group);
            Optional<String> both = group.stream().filter(s -> job.contains(s) && candidate.contains(s)).findFirst();
            if (both.isPresent()) {
                matched.add(both.get());
            } else if (group.stream().anyMatch(job::contains)) {
                missing.add(label);
            } else {
                reasons.add("the job does not mention " + label);
            }
        }
        return new ComponentScore("required-skills", ratio((double) matched.size() / requirements.size()), matched, missing, reasons);
    }

    static ComponentScore preferredSkills(MatchContext c) {
        List<RoleProfileDefinition.WeightedSkill> preferred = c.role().skills().preferred();
        if (preferred.isEmpty()) {
            return noEvidence("preferred-skills", "the role profile has no preferred skills");
        }
        Set<String> job = c.job().skills();
        Set<String> candidate = CandidateFacts.skills(c.candidate());
        int total = preferred.stream().mapToInt(RoleProfileDefinition.WeightedSkill::weight).sum();
        int earned = 0;
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (RoleProfileDefinition.WeightedSkill skill : preferred) {
            if (job.contains(skill.skill()) && candidate.contains(skill.skill())) {
                earned += skill.weight();
                matched.add(skill.skill());
            } else if (job.contains(skill.skill())) {
                missing.add(skill.skill());
            }
        }
        return new ComponentScore("preferred-skills", ratio((double) earned / total), matched, missing, List.of());
    }

    static ComponentScore experience(MatchContext c) {
        Optional<BigDecimal> years = CandidateFacts.totalExperienceYears(c.candidate(), EligibilityRules.asOf(c));
        if (years.isEmpty()) {
            return noEvidence("experience", "no verified experience facts");
        }
        double y = years.get().doubleValue();
        List<Double> fits = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        RoleProfileDefinition.ExperienceRange range = c.role().experience();
        if (range != null && range.minYears() != null && range.minYears() > 0 && y < range.minYears()) {
            fits.add(y / range.minYears());
            reasons.add(y + " years, below the role minimum of " + range.minYears());
        } else if (range != null && range.maxYears() != null && range.maxYears() > 0 && y > range.maxYears()) {
            fits.add(range.maxYears() / y);
            reasons.add(y + " years, above the role maximum of " + range.maxYears());
        } else if (range != null && (range.minYears() != null || range.maxYears() != null)) {
            fits.add(1.0);
        }
        Optional<Integer> required = MatchingSupport.requiredYears(c.job());
        if (required.isPresent()) {
            fits.add(y >= required.get() ? 1.0 : y / required.get());
            reasons.add("the job asks for " + required.get() + "+ years; candidate has " + y);
        }
        if (fits.isEmpty()) {
            return noEvidence("experience", "neither the role nor the job states an experience range");
        }
        double score = fits.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return new ComponentScore("experience", ratio(score), List.of(y + " years of experience"), List.of(), reasons);
    }

    static ComponentScore location(MatchContext c) {
        RoleProfileDefinition.LocationPreferences prefs = c.role().locations();
        if (prefs == null) {
            return noEvidence("location", "the role profile has no location preferences");
        }
        List<Double> parts = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        WorkplaceType workplace = c.job().workplaceType();
        if (!prefs.workplaceTypes().isEmpty() && workplace != null && workplace != WorkplaceType.UNKNOWN) {
            boolean ok = prefs.workplaceTypes().contains(workplace);
            parts.add(ok ? 1.0 : 0.0);
            (ok ? matched : missing).add("workplace " + workplace);
        }
        Set<String> countries = MatchingSupport.jobCountries(c.job());
        Set<String> wanted = prefs.preferred().stream().map(p -> p.country().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!wanted.isEmpty() && !countries.isEmpty()) {
            boolean ok = countries.stream().anyMatch(wanted::contains);
            parts.add(ok ? 1.0 : 0.0);
            (ok ? matched : missing).add("country " + countries);
        }
        if (parts.isEmpty()) {
            return noEvidence("location", "the job's workplace type and country are not known");
        }
        return new ComponentScore("location", ratio(parts.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                matched, missing, List.of());
    }

    static ComponentScore seniority(MatchContext c) {
        List<Seniority> allowed = c.role().titles().seniority();
        Seniority actual = c.job().seniority();
        if (allowed.isEmpty() || actual == null || actual == Seniority.UNKNOWN) {
            return noEvidence("seniority", allowed.isEmpty() ? "no seniority preference" : "the job does not state its seniority");
        }
        boolean ok = allowed.contains(actual);
        return new ComponentScore("seniority", ok ? BigDecimal.ONE.setScale(4) : BigDecimal.ZERO.setScale(4),
                ok ? List.of(actual.name()) : List.of(), ok ? List.of() : List.of(actual.name()), List.of());
    }

    static ComponentScore compensation(MatchContext c) {
        RoleProfileDefinition.CompensationPreference preference = c.role().compensation();
        var pay = c.job().compensation();
        Optional<BigDecimal> top = MatchingSupport.annualTop(pay);
        if (preference == null || preference.minAnnual() == null || preference.minAnnual().signum() == 0) {
            return noEvidence("compensation", "no compensation preference");
        }
        if (top.isEmpty() || !preference.currency().equals(pay.currency())) {
            return noEvidence("compensation", "pay not published in " + preference.currency());
        }
        double value = top.get().divide(preference.minAnnual(), 4, RoundingMode.HALF_UP).doubleValue();
        return new ComponentScore("compensation", ratio(value), List.of("up to " + top.get().toPlainString() + " " + pay.currency()),
                List.of(), List.of());
    }
}
