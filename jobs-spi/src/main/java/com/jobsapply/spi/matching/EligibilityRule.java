package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Plugin;
import java.util.Map;

/**
 * A hard eligibility constraint, referenced from role-profile YAML by {@link #ruleType()}. Must return
 * {@code UNKNOWN} — never {@code PASS} or {@code FAIL} — when required facts are missing.
 */
public interface EligibilityRule extends Plugin {

    String ruleType();

    RuleOutcome evaluate(MatchContext context, Map<String, Object> params);
}
