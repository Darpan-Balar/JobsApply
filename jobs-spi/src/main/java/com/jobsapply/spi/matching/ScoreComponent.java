package com.jobsapply.spi.matching;

import com.jobsapply.spi.common.Plugin;
import java.util.Map;

/** A soft-preference scorer, referenced from role-profile scoring weights by {@link #componentKey()}. */
public interface ScoreComponent extends Plugin {

    String componentKey();

    ComponentScore score(MatchContext context, Map<String, Object> params);
}
