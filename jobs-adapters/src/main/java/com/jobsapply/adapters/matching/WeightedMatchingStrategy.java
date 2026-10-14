package com.jobsapply.adapters.matching;

import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.RuleVerdict;
import com.jobsapply.spi.matching.ComponentScore;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.matching.MatchResult;
import com.jobsapply.spi.matching.MatchingStrategy;
import com.jobsapply.spi.matching.RuleOutcome;
import com.jobsapply.spi.matching.ScoreComponent;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code weighted} v1. Eligibility is the combination of every configured hard rule (all are evaluated, for a complete
 * explanation). The score (0–100) is the weighted mean of the components that have evidence; weights of components
 * without evidence are left out and the resulting coverage is reported, so missing information neither raises nor
 * lowers the score silently. Works without any LLM.
 */
public final class WeightedMatchingStrategy implements MatchingStrategy {

    public static final String KEY = "weighted";
    public static final int VERSION = 1;

    private final Map<String, EligibilityRule> rules;
    private final Map<String, ScoreComponent> components;

    public WeightedMatchingStrategy(Collection<EligibilityRule> rules, Collection<ScoreComponent> components) {
        this.rules = rules.stream().collect(Collectors.toUnmodifiableMap(EligibilityRule::ruleType, Function.identity()));
        this.components = components.stream().collect(Collectors.toUnmodifiableMap(ScoreComponent::componentKey, Function.identity()));
    }

    @Override
    public PluginDescriptor descriptor() {
        return MatchingSupport.descriptor(KEY, PluginKind.MATCHING_STRATEGY);
    }

    @Override
    public String strategyKey() {
        return KEY;
    }

    @Override
    public int strategyVersion() {
        return VERSION;
    }

    @Override
    public MatchResult evaluate(MatchContext context) {
        RoleProfileDefinition role = context.role();
        List<RuleOutcome> outcomes = new ArrayList<>();
        for (RoleProfileDefinition.RuleSpec spec : role.eligibility()) {
            EligibilityRule rule = rules.get(spec.type());
            outcomes.add(rule == null
                    ? new RuleOutcome(spec.type(), RuleVerdict.UNKNOWN, "rule '" + spec.type() + "' is not installed", List.of())
                    : rule.evaluate(context, spec.params()));
        }
        Eligibility eligibility = Eligibility.fromVerdicts(outcomes.stream().map(RuleOutcome::verdict).toList());

        List<ComponentScore> scores = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal coveredWeight = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> weight : role.scoring().weights().entrySet()) {
            totalWeight = totalWeight.add(weight.getValue());
            ScoreComponent component = components.get(weight.getKey());
            ComponentScore score = component == null
                    ? MatchingSupport.noEvidence(weight.getKey(), "component '" + weight.getKey() + "' is not installed")
                    : component.score(context, Map.of());
            scores.add(score);
            if (score.value() != null) {
                weighted = weighted.add(weight.getValue().multiply(score.value()));
                coveredWeight = coveredWeight.add(weight.getValue());
            }
        }
        BigDecimal score = coveredWeight.signum() == 0 ? null
                : weighted.divide(coveredWeight, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal coverage = totalWeight.signum() == 0 ? BigDecimal.ZERO
                : coveredWeight.divide(totalWeight, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);

        List<MatchResult.Requirement> matched = new ArrayList<>();
        List<MatchResult.Requirement> missing = new ArrayList<>();
        List<MatchResult.UncertainFact> uncertain = new ArrayList<>();
        for (ComponentScore s : scores) {
            s.matched().forEach(m -> matched.add(new MatchResult.Requirement(s.component(), m, null)));
            s.missing().forEach(m -> missing.add(new MatchResult.Requirement(s.component(), m, null)));
            if (s.value() == null) {
                uncertain.add(new MatchResult.UncertainFact(s.component(), String.join("; ", s.reasons())));
            }
        }
        outcomes.stream().filter(o -> o.verdict() == RuleVerdict.UNKNOWN)
                .forEach(o -> uncertain.add(new MatchResult.UncertainFact(o.ruleType(), o.explanation())));

        List<String> reasons = new ArrayList<>();
        reasons.add("eligibility " + eligibility + summary(outcomes));
        reasons.add(score == null ? "not scored: no component had evidence"
                : "score " + score.toPlainString() + " from " + coverage.toPlainString() + "% of the weighted evidence");
        BigDecimal minimum = role.scoring().thresholds().minimum();
        BigDecimal shortlist = role.scoring().thresholds().shortlist();
        if (score != null) {
            reasons.add(score.compareTo(shortlist) >= 0 ? "at or above the shortlist threshold " + shortlist
                    : score.compareTo(minimum) >= 0 ? "above the minimum threshold " + minimum
                    : "below the minimum threshold " + minimum);
        }
        return MatchResult.builder()
                .strategyKey(KEY)
                .strategyVersion(VERSION)
                .score(score)
                .eligibility(eligibility)
                .ruleOutcomes(outcomes)
                .componentScores(scores)
                .matched(matched)
                .missing(missing)
                .uncertain(uncertain)
                .reasons(reasons)
                .build();
    }

    private static String summary(List<RuleOutcome> outcomes) {
        List<String> notPassing = outcomes.stream().filter(o -> o.verdict() != RuleVerdict.PASS)
                .map(o -> o.ruleType() + " " + o.verdict() + ": " + o.explanation()).toList();
        return notPassing.isEmpty() ? "" : " (" + String.join("; ", notPassing) + ")";
    }
}
