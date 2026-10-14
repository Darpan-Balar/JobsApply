package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Plugin;

/**
 * Composes eligibility rules and score components into a versioned, explainable result. The configuration (rules,
 * weights, thresholds) comes from {@code context.role()}. Must work without any LLM provider.
 */
public interface MatchingStrategy extends Plugin {

    String strategyKey();

    int strategyVersion();

    MatchResult evaluate(MatchContext context);
}
