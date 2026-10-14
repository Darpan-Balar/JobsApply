package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Lists;
import java.math.BigDecimal;
import java.util.List;

/** @param value normalized score in [0, 1] */
public record ComponentScore(
        String component, BigDecimal value, List<String> matched, List<String> missing, List<String> reasons) {

    public ComponentScore {
        matched = Lists.immutable(matched);
        missing = Lists.immutable(missing);
        reasons = Lists.immutable(reasons);
    }
}
