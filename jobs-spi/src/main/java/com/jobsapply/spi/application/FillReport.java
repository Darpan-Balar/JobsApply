package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import java.util.List;

public record FillReport(List<String> filledFieldIds, List<String> skippedFieldIds, List<Blocker> blockers) {

    public FillReport {
        filledFieldIds = Lists.immutable(filledFieldIds);
        skippedFieldIds = Lists.immutable(skippedFieldIds);
        blockers = Lists.immutable(blockers);
    }
}
