package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.common.Lists;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * Explainable result of evaluating one job for one candidate profile version against one role profile version.
 *
 * @param score 0–100, or {@code null} if scoring was not performed
 */
@Builder(toBuilder = true)
public record MatchResult(
        String strategyKey,
        int strategyVersion,
        BigDecimal score,
        Eligibility eligibility,
        List<RuleOutcome> ruleOutcomes,
        List<ComponentScore> componentScores,
        List<Requirement> matched,
        List<Requirement> missing,
        List<UncertainFact> uncertain,
        List<String> reasons) {

    public MatchResult {
        ruleOutcomes = Lists.immutable(ruleOutcomes);
        componentScores = Lists.immutable(componentScores);
        matched = Lists.immutable(matched);
        missing = Lists.immutable(missing);
        uncertain = Lists.immutable(uncertain);
        reasons = Lists.immutable(reasons);
    }

    /** @param kind e.g. {@code skill}, {@code title}, {@code location} */
    public record Requirement(String kind, String value, String detail) {}

    public record UncertainFact(String subject, String reason) {}
}
