package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.render.ApplicationQuestion;
import java.util.List;

/** Fields discovered on the form, expressed as questions, plus anything blocking automated assistance. */
public record FormInspection(List<ApplicationQuestion> fields, List<Blocker> blockers) {

    public FormInspection {
        fields = Lists.immutable(fields);
        blockers = Lists.immutable(blockers);
    }
}
