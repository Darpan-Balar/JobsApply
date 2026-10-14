package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.common.RuleVerdict;
import com.jobsapply.spi.profile.FactRef;
import java.util.List;

/**
 * @param explanation human-readable reason, always present
 * @param evidence candidate facts the verdict relied on (empty when the verdict is {@code UNKNOWN} for lack of facts)
 */
public record RuleOutcome(String ruleType, RuleVerdict verdict, String explanation, List<FactRef> evidence) {

    public RuleOutcome {
        evidence = Lists.immutable(evidence);
    }
}
