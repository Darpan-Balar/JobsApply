package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import java.util.List;

/**
 * @param skippedFieldIds mappings that were not filled (not HIGH confidence, field missing, option not offered)
 */
public record FillReport(List<String> filledFieldIds, List<String> skippedFieldIds, List<Blocker> blockers) {

    public FillReport {
        filledFieldIds = Lists.immutable(filledFieldIds);
        skippedFieldIds = Lists.immutable(skippedFieldIds);
        blockers = Lists.immutable(blockers);
    }
}
