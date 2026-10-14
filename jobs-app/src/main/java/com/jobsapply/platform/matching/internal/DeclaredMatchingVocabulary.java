package com.jobsapply.platform.matching.internal;

import com.jobsapply.platform.matching.MatchingVocabulary;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Phase 1: the <em>declared</em> built-in vocabulary, so configuration can be validated before matching exists.
 * These names are not yet backed by implementations; Phase 4 replaces this with a vocabulary derived from the
 * registered rule / component / strategy plugins.
 */
@Component
class DeclaredMatchingVocabulary implements MatchingVocabulary {

    private static final Set<String> RULE_TYPES = Set.of(
            "title-match",
            "seniority-in",
            "employment-type-in",
            "workplace-type-in",
            "location-in",
            "min-experience-years",
            "max-required-experience",
            "education-level-min",
            "education-field-in",
            "work-authorization",
            "compensation-min");

    private static final Set<String> SCORE_COMPONENTS = Set.of(
            "title", "required-skills", "preferred-skills", "experience", "location", "seniority", "compensation");

    private static final Set<String> STRATEGIES = Set.of("weighted");

    @Override
    public Set<String> eligibilityRuleTypes() {
        return RULE_TYPES;
    }

    @Override
    public Set<String> scoreComponents() {
        return SCORE_COMPONENTS;
    }

    @Override
    public Set<String> strategies() {
        return STRATEGIES;
    }
}
