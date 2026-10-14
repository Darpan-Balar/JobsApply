package com.jobsapply.adapters.matching;

import static com.jobsapply.adapters.matching.MatchingSupport.fail;
import static com.jobsapply.adapters.matching.MatchingSupport.integer;
import static com.jobsapply.adapters.matching.MatchingSupport.pass;
import static com.jobsapply.adapters.matching.MatchingSupport.stringList;
import static com.jobsapply.adapters.matching.MatchingSupport.unknown;

import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.RuleVerdict;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.matching.RuleOutcome;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateFacts;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The built-in hard eligibility rules. Each is a small {@link EligibilityRule} whose plugin id equals its rule type.
 * Rules fall back to the role profile for their parameters, so most can be used with no {@code params}.
 */
public final class EligibilityRules {

    private EligibilityRules() {}

    /** A rule defined by a function; the descriptor id is the rule type. */
    static final class Rule implements EligibilityRule {
        private final String type;
        private final BiFunction<MatchContext, Map<String, Object>, RuleOutcome> logic;

        Rule(String type, BiFunction<MatchContext, Map<String, Object>, RuleOutcome> logic) {
            this.type = type;
            this.logic = logic;
        }

        @Override
        public PluginDescriptor descriptor() {
            return MatchingSupport.descriptor(type, PluginKind.ELIGIBILITY_RULE);
        }

        @Override
        public String ruleType() {
            return type;
        }

        @Override
        public RuleOutcome evaluate(MatchContext context, Map<String, Object> params) {
            return logic.apply(context, params == null ? Map.of() : params);
        }
    }

    public static List<EligibilityRule> all() {
        return List.of(
                new Rule("title-match", EligibilityRules::titleMatch),
                new Rule("seniority-in", EligibilityRules::seniorityIn),
                new Rule("employment-type-in", EligibilityRules::employmentTypeIn),
                new Rule("workplace-type-in", EligibilityRules::workplaceTypeIn),
                new Rule("location-in", EligibilityRules::locationIn),
                new Rule("min-experience-years", EligibilityRules::minExperienceYears),
                new Rule("max-required-experience", EligibilityRules::maxRequiredExperience),
                new Rule("education-level-min", EligibilityRules::educationLevelMin),
                new Rule("education-field-in", EligibilityRules::educationFieldIn),
                new Rule("work-authorization", EligibilityRules::workAuthorization),
                new Rule("compensation-min", EligibilityRules::compensationMin));
    }

    static RuleOutcome titleMatch(MatchContext c, Map<String, Object> params) {
        String type = "title-match";
        String title = MatchingSupport.normalize(c.job().title());
        if (title.isEmpty()) {
            return unknown(type, "the job has no title");
        }
        RoleProfileDefinition.TitleRules rules = c.role().titles();
        Optional<String> excluded = MatchingSupport.firstPhrase(title, rules.exclude());
        if (excluded.isPresent()) {
            return fail(type, "title contains the excluded phrase '" + excluded.get() + "'");
        }
        return MatchingSupport.firstPhrase(title, rules.include())
                .map(p -> pass(type, "title matches '" + p + "'"))
                .orElseGet(() -> fail(type, "title matches none of: " + String.join(", ", rules.include())));
    }

    static RuleOutcome seniorityIn(MatchContext c, Map<String, Object> params) {
        String type = "seniority-in";
        List<String> allowed = !stringList(params, "seniority").isEmpty() ? stringList(params, "seniority")
                : c.role().titles().seniority().stream().map(Seniority::name).toList();
        return enumIn(type, "seniority", allowed, c.job().seniority(), Seniority.UNKNOWN);
    }

    static RuleOutcome employmentTypeIn(MatchContext c, Map<String, Object> params) {
        List<String> allowed = !stringList(params, "types").isEmpty() ? stringList(params, "types")
                : c.role().employmentTypes().stream().map(EmploymentType::name).toList();
        return enumIn("employment-type-in", "employment type", allowed, c.job().employmentType(), EmploymentType.UNKNOWN);
    }

    static RuleOutcome workplaceTypeIn(MatchContext c, Map<String, Object> params) {
        List<String> allowed = !stringList(params, "types").isEmpty() ? stringList(params, "types")
                : c.role().locations() == null ? List.of()
                : c.role().locations().workplaceTypes().stream().map(WorkplaceType::name).toList();
        return enumIn("workplace-type-in", "workplace type", allowed, c.job().workplaceType(), WorkplaceType.UNKNOWN);
    }

    private static <E extends Enum<E>> RuleOutcome enumIn(String type, String what, List<String> allowed, E actual, E unknownValue) {
        if (allowed.isEmpty()) {
            return pass(type, "no " + what + " constraint");
        }
        if (actual == null || actual == unknownValue) {
            return unknown(type, "the job does not state its " + what);
        }
        return allowed.contains(actual.name())
                ? pass(type, what + " " + actual + " is accepted")
                : fail(type, what + " " + actual + " is not one of " + allowed);
    }

    static RuleOutcome locationIn(MatchContext c, Map<String, Object> params) {
        String type = "location-in";
        Set<String> allowed = (!stringList(params, "countries").isEmpty() ? stringList(params, "countries")
                : c.role().locations() == null ? List.<String>of()
                : c.role().locations().preferred().stream().map(RoleProfileDefinition.LocationPreference::country).toList())
                .stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return pass(type, "no location constraint");
        }
        Set<String> countries = MatchingSupport.jobCountries(c.job());
        if (countries.isEmpty()) {
            return unknown(type, "the job's country is not known");
        }
        return countries.stream().anyMatch(allowed::contains)
                ? pass(type, "job country " + countries + " is accepted")
                : fail(type, "job country " + countries + " is not one of " + allowed);
    }

    static RuleOutcome minExperienceYears(MatchContext c, Map<String, Object> params) {
        String type = "min-experience-years";
        Integer min = integer(params, "years") != null ? integer(params, "years")
                : c.role().experience() == null ? null : c.role().experience().minYears();
        if (min == null) {
            return pass(type, "no minimum experience");
        }
        Optional<BigDecimal> years = CandidateFacts.totalExperienceYears(c.candidate(), asOf(c));
        if (years.isEmpty()) {
            return unknown(type, "no verified experience facts");
        }
        List<FactRef> evidence = refs(c, CandidateFacts.experiences(c.candidate()).map(CandidateExperience::id).toList());
        return years.get().compareTo(BigDecimal.valueOf(min)) >= 0
                ? new RuleOutcome(type, RuleVerdict.PASS, years.get() + " years >= " + min, evidence)
                : new RuleOutcome(type, RuleVerdict.FAIL, years.get() + " years < " + min, evidence);
    }

    static RuleOutcome maxRequiredExperience(MatchContext c, Map<String, Object> params) {
        String type = "max-required-experience";
        Integer max = integer(params, "years") != null ? integer(params, "years")
                : c.role().experience() == null ? null : c.role().experience().maxYears();
        if (max == null) {
            return pass(type, "no maximum");
        }
        return MatchingSupport.requiredYears(c.job())
                .map(required -> required > max
                        ? fail(type, "the job asks for " + required + "+ years (more than " + max + ")")
                        : pass(type, "the job asks for " + required + "+ years"))
                .orElseGet(() -> unknown(type, "the job does not state required years of experience"));
    }

    static RuleOutcome educationLevelMin(MatchContext c, Map<String, Object> params) {
        String type = "education-level-min";
        DegreeLevel min = !stringList(params, "level").isEmpty() ? DegreeLevel.valueOf(stringList(params, "level").getFirst())
                : c.role().education() == null ? null : c.role().education().minimumLevel();
        if (min == null) {
            return pass(type, "no minimum education level");
        }
        List<CandidateEducation> education = CandidateFacts.education(c.candidate()).toList();
        if (education.isEmpty()) {
            return unknown(type, "no verified education facts");
        }
        Optional<CandidateEducation> highest = education.stream().filter(CandidateEducation::completed)
                .max(Comparator.comparing(CandidateEducation::level));
        List<FactRef> evidence = refs(c, education.stream().map(CandidateEducation::id).toList());
        if (highest.isPresent() && highest.get().level().compareTo(min) >= 0) {
            return new RuleOutcome(type, RuleVerdict.PASS, "completed " + highest.get().level() + " >= " + min, evidence);
        }
        return new RuleOutcome(type, RuleVerdict.FAIL,
                "highest completed degree " + highest.map(e -> e.level().name()).orElse("none") + " < " + min, evidence);
    }

    static RuleOutcome educationFieldIn(MatchContext c, Map<String, Object> params) {
        String type = "education-field-in";
        List<String> fields = !stringList(params, "fields").isEmpty() ? stringList(params, "fields")
                : c.role().education() == null ? List.of() : c.role().education().fields();
        if (fields.isEmpty()) {
            return pass(type, "no field constraint");
        }
        List<CandidateEducation> withField = CandidateFacts.education(c.candidate()).filter(e -> e.field() != null).toList();
        if (withField.isEmpty()) {
            return unknown(type, "no verified education field");
        }
        Optional<CandidateEducation> match = withField.stream().filter(e -> fields.stream().anyMatch(f -> fieldMatches(e.field(), f))).findFirst();
        return match.<RuleOutcome>map(e -> new RuleOutcome(type, RuleVerdict.PASS, "studied " + e.field(), refs(c, List.of(e.id()))))
                .orElseGet(() -> new RuleOutcome(type, RuleVerdict.FAIL, "no education in " + fields,
                        refs(c, withField.stream().map(CandidateEducation::id).toList())));
    }

    private static boolean fieldMatches(String studied, String wanted) {
        String a = MatchingSupport.normalize(studied);
        String b = MatchingSupport.normalize(wanted);
        return a.contains(b) || b.contains(a);
    }

    private static final Set<String> AUTHORIZED = Set.of("citizen", "permanent-resident", "authorized", "yes", "not-required");
    private static final Set<String> NOT_AUTHORIZED = Set.of("not-authorized", "no");
    private static final String SPONSORSHIP = "requires-sponsorship";

    /**
     * Candidate attribute {@code work-authorization.<cc>} (e.g. {@code work-authorization.us = citizen}) for the job's
     * countries. {@code params.allowSponsorship=true} accepts {@code requires-sponsorship}.
     */
    static RuleOutcome workAuthorization(MatchContext c, Map<String, Object> params) {
        String type = "work-authorization";
        Set<String> countries = MatchingSupport.jobCountries(c.job());
        if (countries.isEmpty()) {
            return unknown(type, "the job's country is not known");
        }
        boolean allowSponsorship = Boolean.parseBoolean(String.valueOf(params.getOrDefault("allowSponsorship", "false")));
        List<FactRef> evidence = new ArrayList<>();
        int notAuthorized = 0;
        for (String country : countries) {
            Optional<CandidateAttribute> attribute = CandidateFacts.attribute(c.candidate(), "work-authorization." + country.toLowerCase(Locale.ROOT));
            if (attribute.isEmpty()) {
                continue;
            }
            evidence.add(new FactRef(c.candidateProfileVersionId(), attribute.get().id()));
            String value = CandidateFacts.normalize(attribute.get().value());
            if (AUTHORIZED.contains(value) || (allowSponsorship && SPONSORSHIP.equals(value))) {
                return new RuleOutcome(type, RuleVerdict.PASS, "authorized to work in " + country + " (" + value + ")", evidence);
            }
            if (NOT_AUTHORIZED.contains(value) || SPONSORSHIP.equals(value)) {
                notAuthorized++;
            }
        }
        if (notAuthorized == countries.size()) {
            return new RuleOutcome(type, RuleVerdict.FAIL, "not authorized to work in " + countries + " without sponsorship", evidence);
        }
        return new RuleOutcome(type, RuleVerdict.UNKNOWN, "no verified work authorization for " + countries, evidence);
    }

    static RuleOutcome compensationMin(MatchContext c, Map<String, Object> params) {
        String type = "compensation-min";
        RoleProfileDefinition.CompensationPreference preference = c.role().compensation();
        if (preference == null || preference.minAnnual() == null) {
            return pass(type, "no compensation minimum");
        }
        var pay = c.job().compensation();
        Optional<BigDecimal> top = MatchingSupport.annualTop(pay);
        if (pay == null || top.isEmpty() || pay.currency() == null) {
            return preference.whenMissing() == RoleProfileDefinition.CompensationPolicy.IGNORE
                    ? pass(type, "pay not published (ignored by the profile's policy)")
                    : unknown(type, "pay or pay period not published");
        }
        if (!pay.currency().equals(preference.currency())) {
            return unknown(type, "pay is in " + pay.currency() + ", not " + preference.currency());
        }
        return top.get().compareTo(preference.minAnnual()) >= 0
                ? pass(type, "annual pay up to " + top.get().toPlainString() + " " + pay.currency())
                : fail(type, "annual pay up to " + top.get().toPlainString() + " " + pay.currency() + " < " + preference.minAnnual());
    }

    static YearMonth asOf(MatchContext c) {
        return YearMonth.from(c.evaluatedAt().atZone(ZoneOffset.UTC));
    }

    private static List<FactRef> refs(MatchContext c, List<String> factIds) {
        return factIds.stream().map(id -> new FactRef(c.candidateProfileVersionId(), id)).toList();
    }
}
