package com.jobsapply.platform.matching;

import java.util.Set;

/**
 * The names that role-profile configuration may reference: eligibility rule types, score components and strategies.
 * Used to reject typos at configuration-validation time.
 */
public interface MatchingVocabulary {

    Set<String> eligibilityRuleTypes();

    Set<String> scoreComponents();

    Set<String> strategies();
}
